package com.pr0gramm.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Logger
import com.pr0gramm.app.util.catchAll
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.setConstraintsCompat
import java.util.concurrent.TimeUnit

class SyncWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = retryOnError {
        val sourceTag = inputData.getString("sourceTag") ?: "unknown"
        logger.info { "Sync worker started (started by $sourceTag)" }

        // schedule next sync
        scheduleNextSyncIn(context,
                nextIntervalInMillis(), TimeUnit.MILLISECONDS,
                append = true, sourceTag = "NextSync (triggered by ${sourceTag.take(64)}...)")

        // get service and sync now.
        val syncService = context.injector.instance<SyncService>()
        catchAll { syncService.sync() }

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

        fun syncNow(ctx: Context) {
            // start normal sync cycle now.
            scheduleNextSyncIn(ctx, 0, TimeUnit.MILLISECONDS, sourceTag = "SyncNow")
        }

        fun scheduleNextSync(ctx: Context, sourceTag: String) {
            scheduleNextSyncIn(ctx, DEFAULT_SYNC_DELAY_MS, TimeUnit.MILLISECONDS, sourceTag = sourceTag)
        }

        fun scheduleNextSyncIn(ctx: Context, delay: Long = DEFAULT_SYNC_DELAY_MS, unit: TimeUnit = TimeUnit.MILLISECONDS, append: Boolean = false, sourceTag: String? = null) = doInBackground {

            val delayInMilliseconds = unit.toMillis(delay)
            logger.info { "Scheduling sync-job to run in ${Duration.millis(delayInMilliseconds)} (append=$append)" }

            val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val data = workDataOf("delay" to delayInMilliseconds, "sourceTag" to sourceTag)

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setInitialDelay(delay, unit)
                    .setConstraintsCompat(constraints)
                    .setInputData(data)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                    .build()

            val policy = if (append) ExistingWorkPolicy.APPEND else ExistingWorkPolicy.REPLACE

            val wm = WorkManager.getInstance(ctx)
            wm.enqueueUniqueWork("Sync", policy, request)
        }
    }
}
