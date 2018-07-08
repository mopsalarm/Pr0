package com.pr0gramm.app.sync

import com.evernote.android.job.DailyJob
import com.evernote.android.job.JobRequest
import com.pr0gramm.app.util.SimpleJobCreator
import com.pr0gramm.app.util.directKodein
import com.pr0gramm.app.util.doInBackground
import org.kodein.di.erased.instance
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class SyncStatisticsJob : DailyJob() {
    private val logger = LoggerFactory.getLogger("SyncJob")

    override fun onRunDailyJob(params: Params): DailyJobResult {
        logger.info("Sync statistics job started.")

        // get service and sync now.
        val syncService = context.directKodein.instance<SyncService>()
        syncService.syncStatistics()

        return DailyJobResult.SUCCESS
    }

    companion object {
        val CREATOR = SimpleJobCreator.forSupplier("sync-statistics") { SyncStatisticsJob() }

        fun schedule() {
            val builder = JobRequest.Builder("sync-statistics").apply {
                setUpdateCurrent(true)
                setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
            }

            doInBackground {
                // run in the night if we have network
                DailyJob.schedule(builder, TimeUnit.HOURS.toMillis(0), TimeUnit.HOURS.toMillis(6))
            }
        }
    }
}