package com.example.aegis.data.repository

import android.content.Context
import com.example.aegis.R
import com.example.aegis.data.local.db.AegisDatabase
import com.example.aegis.data.local.db.CachedListingEntity
import com.example.aegis.data.local.db.ListingCacheDao
import com.example.aegis.data.local.db.PendingAction
import com.example.aegis.data.local.db.PendingChangeEntity
import com.example.aegis.data.local.db.listingFromSnapshotJson
import com.example.aegis.data.local.db.toEntity
import com.example.aegis.data.local.db.toSignup
import com.example.aegis.data.local.db.toSnapshotJson
import com.example.aegis.data.sync.ConnectivityObserver
import com.example.aegis.data.sync.SyncScheduler
import com.example.aegis.data.sync.SyncState
import com.example.aegis.domain.model.ApiError
import com.example.aegis.domain.model.ApiResult
import com.example.aegis.domain.model.HoursSummary
import com.example.aegis.domain.model.Listing
import com.example.aegis.domain.model.ListingDraft
import com.example.aegis.domain.model.ListingType
import com.example.aegis.domain.model.Signup
import com.example.aegis.domain.model.SignupStatus
import com.example.aegis.domain.repository.ListingRepository
import com.example.aegis.domain.repository.SignupRepository
import java.util.UUID

/*
 * Offline-first versions of the repositories. The ViewModels don't change: they still depend on
 * ListingRepository / SignupRepository. These wrap the Retrofit ones (ApiListingRepository /
 * ApiSignupRepository) and add the local copy.
 *
 * The rule for every call: try the server when the phone looks online; if that fails because there
 * is no connection (ApiError.Kind.NETWORK), fall back to the local copy. Any OTHER error (409 full,
 * 403, 401...) is a real answer from the server and is returned as is - never hidden by the cache.
 */

/** One cache slot per combination of filters, so a filtered feed never overwrites the unfiltered one. */
internal fun listingQueryKey(
    causes: List<String>,
    skills: List<String>,
    type: ListingType?,
    area: String?
): String =
    "c=${causes.sorted().joinToString(",")}" +
            "|s=${skills.sorted().joinToString(",")}" +
            "|t=${type?.apiValue.orEmpty()}" +
            "|a=${area.orEmpty().trim().lowercase()}"

// =================================================================== listings

class OfflineListingRepository(
    private val remote: ListingRepository,
    private val cache: ListingCacheDao,
    private val connectivity: ConnectivityObserver,
    private val syncState: SyncState,
    private val appContext: Context
) : ListingRepository {

    override suspend fun getListings(
        causes: List<String>,
        skills: List<String>,
        type: ListingType?,
        area: String?
    ): ApiResult<List<Listing>> {
        val key = listingQueryKey(causes, skills, type, area)

        if (connectivity.isOnline) {
            when (val result = remote.getListings(causes, skills, type, area)) {
                is ApiResult.Success -> {
                    cache.replaceQuery(
                        key,
                        result.data.mapIndexed { index, listing ->
                            CachedListingEntity(
                                queryKey = key,
                                position = index,
                                listingId = listing.id,
                                json = listing.toSnapshotJson()
                            )
                        }
                    )
                    syncState.markSynced()
                    return result
                }
                is ApiResult.Failure -> if (result.error.kind != ApiError.Kind.NETWORK) return result
            }
        }

        val saved = cache.jsonForQuery(key).mapNotNull { listingFromSnapshotJson(it) }
        return if (saved.isNotEmpty()) ApiResult.Success(saved) else nothingSaved()
    }

    override suspend fun getListing(id: String): ApiResult<Listing> {
        if (connectivity.isOnline) {
            val result = remote.getListing(id)
            if (result is ApiResult.Success) return result
            if (result is ApiResult.Failure && result.error.kind != ApiError.Kind.NETWORK) return result
        }
        val saved = listingFromSnapshotJson(cache.jsonForListing(id))
        return if (saved != null) ApiResult.Success(saved) else nothingSaved()
    }

    // Organisation edits need the server (they change what everyone sees) - not queued offline.
    override suspend fun createListing(draft: ListingDraft) = remote.createListing(draft)
    override suspend fun updateListing(id: String, draft: ListingDraft) = remote.updateListing(id, draft)
    override suspend fun deleteListing(id: String) = remote.deleteListing(id)

    private fun nothingSaved() = ApiResult.Failure(
        ApiError(ApiError.Kind.NETWORK, appContext.getString(R.string.offline_nothing_saved))
    )
}

// =================================================================== sign-ups

class OfflineSignupRepository(
    private val remote: SignupRepository,
    private val db: AegisDatabase,
    private val connectivity: ConnectivityObserver,
    private val syncState: SyncState,
    private val appContext: Context
) : SignupRepository {

    private val commitments get() = db.commitmentDao()
    private val pending get() = db.pendingDao()
    private val listingCache get() = db.listingCacheDao()

    // ------------------------------------------------------------ volunteer: sign up
    override suspend fun signUp(listingId: String): ApiResult<Signup> {
        // Cheap local check first, so tapping "I'd like to help" twice offline doesn't queue two.
        if (mergedCommitments().any { it.listingId == listingId && it.status == SignupStatus.CONFIRMED }) {
            return ApiResult.Failure(
                ApiError(
                    ApiError.Kind.CONFLICT,
                    appContext.getString(R.string.sync_already_signed_up),
                    code = "already_signed_up"
                )
            )
        }

        if (connectivity.isOnline) {
            when (val result = remote.signUp(listingId)) {
                is ApiResult.Success -> {
                    val signup = result.data.withListing(listingId)
                    commitments.upsert(signup.toEntity(position = -1)) // newest first
                    return ApiResult.Success(signup)
                }
                is ApiResult.Failure -> if (result.error.kind != ApiError.Kind.NETWORK) return result
            }
        }
        return queueSignUp(listingId)
    }

    /** No connection: remember the sign-up on the phone. It shows up in Commitments straight away. */
    private suspend fun queueSignUp(listingId: String): ApiResult<Signup> {
        val listingJson = listingCache.jsonForListing(listingId)
        val id = UUID.randomUUID().toString() // becomes the sign-up's id on the server too
        pending.insert(
            PendingChangeEntity(
                signupId = id,
                action = PendingAction.SIGNUP,
                listingId = listingId,
                listingJson = listingJson,
                changedAt = System.currentTimeMillis()
            )
        )
        SyncScheduler.syncNow(appContext)
        return ApiResult.Success(
            Signup(
                id = id,
                listingId = listingId,
                volunteerId = null,
                volunteerName = null,
                status = SignupStatus.CONFIRMED,
                hoursCompleted = null,
                listing = listingFromSnapshotJson(listingJson),
                pendingSync = true
            )
        )
    }

    // ------------------------------------------------------------ volunteer: commitments
    override suspend fun getMySignups(): ApiResult<List<Signup>> {
        if (connectivity.isOnline) {
            when (val result = remote.getMySignups()) {
                is ApiResult.Success -> {
                    commitments.replaceAll(result.data.mapIndexed { index, signup -> signup.toEntity(index) })
                    syncState.markSynced()
                    // Changes still waiting are layered on top, so they don't vanish from the screen.
                    if (pending.allUnsynced().isNotEmpty()) SyncScheduler.syncNow(appContext)
                    return ApiResult.Success(mergedCommitments())
                }
                is ApiResult.Failure -> if (result.error.kind != ApiError.Kind.NETWORK) return result
            }
        }

        val saved = mergedCommitments()
        return if (saved.isNotEmpty() || syncState.hasEverSynced) {
            ApiResult.Success(saved)
        } else {
            ApiResult.Failure(ApiError(ApiError.Kind.NETWORK, appContext.getString(R.string.offline_nothing_saved)))
        }
    }

    // ------------------------------------------------------------ volunteer: dismiss
    override suspend fun cancelSignup(signupId: String): ApiResult<Signup> {
        // Signed up offline and never reached the server: nothing to cancel there - just forget it.
        pending.findUnsynced(signupId, PendingAction.SIGNUP)?.let { queued ->
            pending.delete(queued.seq)
            return ApiResult.Success(
                Signup(
                    id = queued.signupId,
                    listingId = queued.listingId.orEmpty(),
                    volunteerId = null,
                    volunteerName = null,
                    status = SignupStatus.CANCELLED,
                    hoursCompleted = null,
                    listing = listingFromSnapshotJson(queued.listingJson)
                )
            )
        }

        val cached = commitments.find(signupId)

        if (connectivity.isOnline) {
            when (val result = remote.cancelSignup(signupId)) {
                is ApiResult.Success -> {
                    val signup = result.data.copy(listing = result.data.listing ?: cached?.toSignup()?.listing)
                    commitments.upsert(signup.toEntity(position = cached?.position ?: 0))
                    return ApiResult.Success(signup)
                }
                is ApiResult.Failure -> if (result.error.kind != ApiError.Kind.NETWORK) return result
            }
        }

        val row = cached ?: return ApiResult.Failure(
            ApiError(ApiError.Kind.NOT_FOUND, appContext.getString(R.string.sync_signup_not_on_phone))
        )
        // Already queued (tapped twice): don't queue a second cancel.
        if (pending.findUnsynced(signupId, PendingAction.CANCEL) == null) {
            pending.insert(
                PendingChangeEntity(
                    signupId = signupId,
                    action = PendingAction.CANCEL,
                    listingId = row.listingId,
                    listingJson = row.listingJson,
                    changedAt = System.currentTimeMillis()
                )
            )
            SyncScheduler.syncNow(appContext)
        }
        return ApiResult.Success(row.toSignup().copy(status = SignupStatus.CANCELLED, pendingSync = true))
    }

    // ------------------------------------------------------------ not available offline
    // Organisation actions and hours need the server; they fail with the normal "can't reach the server" message.
    override suspend fun getListingSignups(listingId: String) = remote.getListingSignups(listingId)
    override suspend fun completeSignup(signupId: String, hoursCompleted: Double?) =
        remote.completeSignup(signupId, hoursCompleted)

    override suspend fun getHoursSummary(): ApiResult<HoursSummary> = remote.getHoursSummary()

    // ------------------------------------------------------------ helpers
    /** What the server says, with the queue laid on top: queued sign-ups added, queued cancels applied. */
    private suspend fun mergedCommitments(): List<Signup> {
        val result = commitments.all().map { it.toSignup() }.toMutableList()
        for (change in pending.allUnsynced()) {
            when (change.action) {
                PendingAction.SIGNUP -> {
                    if (result.none { it.id == change.signupId }) {
                        result.add(
                            0,
                            Signup(
                                id = change.signupId,
                                listingId = change.listingId.orEmpty(),
                                volunteerId = null,
                                volunteerName = null,
                                status = SignupStatus.CONFIRMED,
                                hoursCompleted = null,
                                listing = listingFromSnapshotJson(change.listingJson),
                                pendingSync = true
                            )
                        )
                    }
                }
                PendingAction.CANCEL -> {
                    val index = result.indexOfFirst { it.id == change.signupId }
                    if (index >= 0) result[index] = result[index].copy(status = SignupStatus.CANCELLED, pendingSync = true)
                }
            }
        }
        return result
    }

    /** POST /listings/{id}/signups doesn't send the listing back; reuse the cached copy for the Commitments card. */
    private suspend fun Signup.withListing(listingId: String): Signup =
        if (listing != null) this
        else copy(listing = listingFromSnapshotJson(listingCache.jsonForListing(listingId)))
}