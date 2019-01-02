package com.pr0gramm.app.sync

import com.evernote.android.job.DailyJob
import com.evernote.android.job.JobRequest
import com.pr0gramm.app.util.Logger
import com.pr0gramm.app.util.SimpleJobCreator
import com.pr0gramm.app.util.di.injector
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class SyncStatisticsJob : DailyJob() {
    private val logger = Logger("SyncJob")

    override fun onRunDailyJob(params: Params): DailyJobResult {
        logger.info { "Sync statistics job started." }

        // get service and sync now.
        val syncService = context.injector.instance<SyncService>()

        runBlocking {
            syncService.syncStatistics()
        }

        return DailyJobResult.SUCCESS
    }

    companion object {
        val CREATOR = SimpleJobCreator.forSupplier("sync-statistics") { SyncStatisticsJob() }

        fun schedule() {
            val builder = JobRequest.Builder("sync-statistics").apply {
                setUpdateCurrent(true)
                setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
            }

            // run in the night if we have network
            DailyJob.scheduleAsync(builder, TimeUnit.HOURS.toMillis(0), TimeUnit.HOURS.toMillis(6))
        }
    }
}