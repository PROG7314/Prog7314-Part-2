package com.example.aegis.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.aegis.data.ServiceLocator
import com.example.aegis.data.repository.SyncEngine

/**
 * Background sync. All the logic is in SyncEngine; this only reports to WorkManager whether to
 * try again. WorkManager starts the app process first, so ServiceLocator is already set up.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        when (ServiceLocator.syncEngine.syncPending()) {
            SyncEngine.Outcome.Done -> Result.success()
            // No signal or a server hiccup: WorkManager retries with backoff. After a few tries we stop;
            // the next reconnect, the 15-minute job or the next queued change starts it again.
            SyncEngine.Outcome.Retry -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            // Signed out, forbidden...: retrying won't help.
            SyncEngine.Outcome.GiveUp -> Result.failure()
        }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}