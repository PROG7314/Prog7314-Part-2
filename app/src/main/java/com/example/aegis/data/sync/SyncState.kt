package com.example.aegis.data.sync

import android.content.Context
import com.example.aegis.R
import com.example.aegis.data.local.db.PendingChangeDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormat
import java.util.Date

/**
 * What the UI needs to know about syncing:
 *  - pendingCount   changes made offline that the server hasn't seen yet (banner, pending icons)
 *  - lastSyncAt     last time the phone and server agreed (Settings: "Last synced ...")
 *  - syncFinished   fires after a batch was sent, so screens can reload
 *  - events         one-off messages, e.g. "Couldn't sign you up for X: it filled up while you were offline"
 */
class SyncState(
    context: Context,
    pendingDao: PendingChangeDao,
    scope: CoroutineScope
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _lastSyncAt = MutableStateFlow(prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0L })
    val lastSyncAt: StateFlow<Long?> = _lastSyncAt.asStateFlow()

    val pendingCount: StateFlow<Int> =
        pendingDao.observeUnsyncedCount().stateIn(scope, SharingStarted.Eagerly, 0)

    private val _syncFinished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val syncFinished: SharedFlow<Unit> = _syncFinished.asSharedFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** True once we've talked to the server at least once, i.e. an empty cache really means "nothing yet". */
    val hasEverSynced: Boolean get() = _lastSyncAt.value != null

    fun markSynced(now: Long = System.currentTimeMillis()) {
        _lastSyncAt.value = now
        prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
    }

    fun notifySyncFinished() {
        _syncFinished.tryEmit(Unit)
    }

    fun emitMessage(message: String) {
        _events.tryEmit(message)
    }

    /** Logout: the next account must not inherit this one's sync time. */
    fun reset() {
        _lastSyncAt.value = null
        prefs.edit().remove(KEY_LAST_SYNC).apply()
    }

    /** "Last synced: 27 Oct 2026, 15:20" or "Not synced yet" - for the Settings screen. */
    fun lastSyncLabel(context: Context): String {
        val at = _lastSyncAt.value ?: return context.getString(R.string.settings_never_synced)
        val text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(at))
        return context.getString(R.string.settings_last_synced, text)
    }

    private companion object {
        const val PREFS = "aegis_sync"
        const val KEY_LAST_SYNC = "last_sync_at"
    }
}