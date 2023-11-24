package com.pr0gramm.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pr0gramm.app.Logger
import com.pr0gramm.app.util.debugOnly
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.setConstraintsCompat
import java.util.concurrent.TimeUnit

class SyncStatsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val logger = Logger("SyncStatsWorker")

    override suspend fun doWork(): Result = retryOnError {
        logger.info { "Sync statistics job started." }

        // get service and sync now.
        val syncService = applicationContext.injector.instance<SyncService>()
        syncService.dailySync()

        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val builder = PeriodicWorkRequestBuilder<SyncStatsWorker>(
                    repeatInterval = 24, repeatIntervalTimeUnit = TimeUnit.HOURS,
                    flexTimeInterval = 6, flexTimeIntervalUnit = TimeUnit.HOURS)

            val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                    "SyncStats", ExistingPeriodicWorkPolicy.KEEP,
                    builder.setConstraintsCompat(constraints).build())

            debugOnly {
                doInBackground {
                    val syncService = ctx.injector.instance<SyncService>()
                    syncService.dailySync()
                }
            }
        }
    }
}