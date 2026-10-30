package com.example.aegis.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * WorkManager wiring. Both jobs only run while the phone has a network connection, survive the app
 * being closed, and are "unique" so queueing ten changes never starts ten workers.
 *
 *  - syncNow:          one run, as soon as there is a connection (used when a change is queued, and on reconnect)
 *  - schedulePeriodic: a safety net every 15 minutes (WorkManager's minimum), in case a run was missed
 */
object SyncScheduler {

    private const val ONE_TIME = "aegis-sync"
    private const val PERIODIC = "aegis-sync-periodic"

    private val needsNetwork = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(needsNetwork)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // If a run is already going, queue this one behind it instead of cutting it off mid-request.
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(needsNetwork)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Logout: stop syncing the old account's changes. */
    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        wm.cancelUniqueWork(ONE_TIME)
        wm.cancelUniqueWork(PERIODIC)
    }
}