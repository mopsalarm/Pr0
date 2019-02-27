package com.pr0gramm.app.sync

import android.content.Context
import androidx.work.*
import com.pr0gramm.app.Logger
import com.pr0gramm.app.services.Track.context
import com.pr0gramm.app.util.di.injector
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        logger.info { "Sync worker started." }

        // schedule next sync
        scheduleNextSyncIn(nextIntervalInMillis(), TimeUnit.MILLISECONDS)

        // get service and sync now.
        val syncService = context.injector.instance<SyncService>()
        syncService.sync()

        return Result.success()
    }

    private fun nextIntervalInMillis(): Long {
        val previousDelay = inputData.getLong("delay", DEFAULT_SYNC_DELAY_MS)
        val delay = 2 * previousDelay

        // put the delay into sane defaults.
        return delay.coerceIn(TimeUnit.MINUTES.toMillis(1), TimeUnit.HOURS.toMillis(1))
    }

    companion object {
        private val logger = Logger("SyncWorker")
        private val DEFAULT_SYNC_DELAY_MS = TimeUnit.MINUTES.toMillis(5)

        fun syncNow() {
            // start normal sync cycle now.
            scheduleNextSyncIn(0, TimeUnit.MILLISECONDS)
        }

        fun scheduleNextSync() {
            scheduleNextSyncIn(DEFAULT_SYNC_DELAY_MS, TimeUnit.MILLISECONDS)
        }

        fun scheduleNextSyncIn(delay: Long, unit: TimeUnit) {
            val delayInMilliseconds = unit.toMillis(delay)
            logger.info { "Scheduling sync-job to run in ${delayInMilliseconds / 1000} seconds" }

            val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setInitialDelay(delay, unit)
                    .setConstraints(constraints)
                    .setInputData(workDataOf("delay" to delayInMilliseconds))
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                    .build()

            WorkManager.getInstance().enqueueUniqueWork(
                    "Sync", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
