package com.example.aegis.data.repository

import android.content.Context
import com.example.aegis.R
import com.example.aegis.data.local.db.AegisDatabase
import com.example.aegis.data.local.db.PendingAction
import com.example.aegis.data.local.db.PendingChangeEntity
import com.example.aegis.data.local.db.SyncStatus
import com.example.aegis.data.local.db.listingFromSnapshotJson
import com.example.aegis.data.local.db.toEntity
import com.example.aegis.data.remote.AegisApi
import com.example.aegis.data.remote.dto.SyncChangeDto
import com.example.aegis.data.remote.dto.SyncRequest
import com.example.aegis.data.remote.dto.SyncResponse
import com.example.aegis.data.remote.dto.SyncResultDto
import com.example.aegis.data.remote.safeApiCall
import com.example.aegis.data.sync.SyncState
import com.example.aegis.domain.model.ApiError
import com.example.aegis.domain.model.ApiResult
import com.example.aegis.domain.model.Signup
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Sends the offline queue (pending_changes) to POST /sync/signups and applies the answer.
 * Lives in data.repository because it reuses the DTO -> domain mappers there.
 *
 * What the server's per-change status means here (backend README, section 6c):
 *   applied / duplicate  done -> mark synced, keep the server's copy of the sign-up
 *   stale                the server's version is newer and wins -> mark synced, keep the server's copy
 *   rejected             refused (listing full, event over...) -> mark rejected, tell the user why
 */
class SyncEngine(
    private val appContext: Context,
    private val api: AegisApi,
    private val db: AegisDatabase,
    private val state: SyncState
) {

    sealed interface Outcome {
        /** Queue is empty (or was sent). */
        data object Done : Outcome

        /** No signal / server problem: try again later. */
        data object Retry : Outcome

        /** Retrying cannot help (signed out, forbidden...). */
        data object GiveUp : Outcome
    }

    // Reconnect, the 15-minute job and a freshly queued change can all fire together; run one at a time.
    private val mutex = Mutex()

    suspend fun syncPending(): Outcome = mutex.withLock {
        val pendingDao = db.pendingDao()
        var appliedAny = false
        var rounds = 0

        while (rounds < MAX_ROUNDS) {
            rounds++
            val batch = pendingDao.unsynced(BATCH_SIZE)
            if (batch.isEmpty()) break

            val request = SyncRequest(batch.map { it.toDto() })
            when (val result = safeApiCall(TAG) { api.syncSignups(request) }) {
                is ApiResult.Failure -> {
                    when (result.error.kind) {
                        ApiError.Kind.NETWORK, ApiError.Kind.SERVER, ApiError.Kind.UNKNOWN ->
                            return@withLock Outcome.Retry
                        // The server called the whole batch invalid. Retrying forever would block every
                        // later change, so set this batch aside and tell the user.
                        ApiError.Kind.VALIDATION -> {
                            val message = appContext.getString(R.string.sync_rejected_generic)
                            batch.forEach { pendingDao.setState(it.seq, SyncStatus.REJECTED, message) }
                            state.emitMessage(message)
                        }
                        else -> return@withLock Outcome.GiveUp
                    }
                }
                is ApiResult.Success -> {
                    // A response that settles nothing would loop forever - treat it as "try later".
                    if (applyResults(batch, result.data) == 0) return@withLock Outcome.Retry
                    appliedAny = true
                }
            }
        }

        if (appliedAny) {
            refreshCommitments()
            pendingDao.pruneFinished(System.currentTimeMillis() - KEEP_FINISHED_MS)
            state.markSynced()
            state.notifySyncFinished()
        }
        Outcome.Done
    }

    /** Returns how many of the batch's changes got a result. */
    private suspend fun applyResults(batch: List<PendingChangeEntity>, response: SyncResponse): Int {
        val pendingDao = db.pendingDao()
        val commitments = db.commitmentDao()
        val byChange = response.results.associateBy { it.id to it.action }
        var handled = 0

        for (row in batch) {
            val result = byChange[row.signupId to row.action] ?: continue
            when (result.status) {
                "applied", "duplicate", "stale" -> {
                    pendingDao.setState(row.seq, SyncStatus.SYNCED, null)
                    result.signup?.toDomain()?.let { storeServerCopy(it, row) }
                    handled++
                }
                "rejected" -> {
                    if (result.code == "already_signed_up") {
                        // The first attempt got through but its reply was lost. It is already ours.
                        pendingDao.setState(row.seq, SyncStatus.SYNCED, null)
                    } else {
                        val message = rejectionMessage(row, result)
                        pendingDao.setState(row.seq, SyncStatus.REJECTED, message)
                        if (row.action == PendingAction.CANCEL && result.code == "not_found") {
                            commitments.deleteById(row.signupId)
                        }
                        state.emitMessage(message)
                    }
                    handled++
                }
                // Anything else comes from a newer server than this app knows: leave it queued.
            }
        }
        return handled
    }

    private suspend fun storeServerCopy(signup: Signup, row: PendingChangeEntity) {
        val commitments = db.commitmentDao()
        val existing = commitments.find(signup.id)
        val listing = signup.listing
            ?: listingFromSnapshotJson(row.listingJson)
            ?: listingFromSnapshotJson(existing?.listingJson)
        commitments.upsert(signup.copy(listing = listing).toEntity(position = existing?.position ?: -1))
    }

    /** Best effort: pull the server's full list so the cache matches it exactly. */
    private suspend fun refreshCommitments() {
        val result = safeApiCall(TAG) { api.getMySignups().map { it.toDomain() } }
        if (result is ApiResult.Success) {
            db.commitmentDao().replaceAll(result.data.mapIndexed { index, signup -> signup.toEntity(index) })
        }
    }

    private fun rejectionMessage(row: PendingChangeEntity, result: SyncResultDto): String {
        val title = listingFromSnapshotJson(row.listingJson)?.title
        val what = title?.let { "\"$it\"" } ?: appContext.getString(R.string.sync_that_listing)
        return when (result.code) {
            "listing_full" -> appContext.getString(R.string.sync_rejected_full, what)
            "listing_ended" -> appContext.getString(R.string.sync_rejected_ended, what)
            "not_found" ->
                if (row.action == PendingAction.SIGNUP) appContext.getString(R.string.sync_rejected_removed, what)
                else appContext.getString(R.string.sync_rejected_gone)
            "already_completed" -> appContext.getString(R.string.sync_rejected_completed, what)
            else -> result.message?.takeIf { it.isNotBlank() } ?: appContext.getString(R.string.sync_rejected_generic)
        }
    }

    private fun PendingChangeEntity.toDto() = SyncChangeDto(
        id = signupId,
        action = action,
        listingId = if (action == PendingAction.SIGNUP) listingId else null,
        updatedAt = Instant.ofEpochMilli(changedAt).toString() // ISO-8601 in UTC
    )

    private companion object {
        const val TAG = "SyncEngine"
        const val BATCH_SIZE = 100          // the API accepts at most 100 changes per request
        const val MAX_ROUNDS = 20
        const val KEEP_FINISHED_MS = 24L * 60 * 60 * 1000
    }
}