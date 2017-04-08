package com.pr0gramm.app.sync

import android.content.Context
import android.content.Intent
import com.evernote.android.job.Job
import com.evernote.android.job.JobManager
import com.evernote.android.job.JobRequest
import com.evernote.android.job.util.support.PersistableBundleCompat
import com.pr0gramm.app.util.SimpleJobCreator
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class SyncJob private constructor() : Job() {
    override fun onRunJob(params: Job.Params): Job.Result {
        logger.info("Sync job started.")

        // schedule next sync
        scheduleNextSyncIn(nextInterval(params), TimeUnit.MILLISECONDS)

        // start the sync service that does the real sync.
        logger.info("Start the SyncIntentService now")
        startWakefulService(Intent(context, SyncIntentService::class.java))

        return Job.Result.SUCCESS
    }

    private fun nextInterval(params: Job.Params): Long {
        val previousDelay = params.extras.getLong("delay", DEFAULT_SYNC_DELAY_MS)
        return Math.min(2 * previousDelay, TimeUnit.HOURS.toMillis(1))
    }

    companion object {
        val CREATOR = SimpleJobCreator.forSupplier("sync") { SyncJob() }

        private val DEFAULT_SYNC_DELAY_MS = TimeUnit.MINUTES.toMillis(5)

        private val logger = LoggerFactory.getLogger("SyncJob")

        fun syncNow(context: Context) {
            // start normal sync cycle now.
            scheduleNextSync()

            // delegate sync to sync service now.
            val intent = Intent(context, SyncIntentService::class.java)
            context.startService(intent)
        }

        fun scheduleNextSync() {
            scheduleNextSyncIn(DEFAULT_SYNC_DELAY_MS, TimeUnit.MILLISECONDS)
        }

        fun scheduleNextSyncIn(delay: Long, unit: TimeUnit) {
            val delayInMilliseconds = unit.toMillis(delay)
            logger.info("Scheduling sync-job to run in {} seconds", delayInMilliseconds / 1000)

            val extras = PersistableBundleCompat()
            extras.putLong("delay", delayInMilliseconds)

            JobManager.instance().schedule(JobRequest.Builder("sync")
                    .setUpdateCurrent(true)
                    .setPersisted(true)
                    .setExact(delayInMilliseconds)
                    .setExtras(extras)
                    .setRequiredNetworkType(JobRequest.NetworkType.ANY)
                    .build())
        }
    }
}
