package com.pr0gramm.app.sync

import com.evernote.android.job.Job
import com.evernote.android.job.JobManager
import com.evernote.android.job.JobRequest
import com.evernote.android.job.util.support.PersistableBundleCompat
import com.pr0gramm.app.util.SimpleJobCreator
import com.pr0gramm.app.util.directKodein
import com.pr0gramm.app.util.doInBackground
import org.kodein.di.erased.instance
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class SyncJob : Job() {
    override fun onRunJob(params: Job.Params): Job.Result {
        logger.info("Sync job started.")

        // schedule next sync
        scheduleNextSyncIn(nextIntervalInMillis(params), TimeUnit.MILLISECONDS)

        // get service and sync now.
        val syncService = context.directKodein.instance<SyncService>()
        syncService.sync()

        return Job.Result.SUCCESS
    }

    private fun nextIntervalInMillis(params: Job.Params): Long {
        val previousDelay = params.extras.getLong("delay", DEFAULT_SYNC_DELAY_MS)
        val delay = 2 * previousDelay

        // put the delay into sane defaults.
        return delay.coerceIn(TimeUnit.MINUTES.toMillis(1), TimeUnit.HOURS.toMillis(1))
    }

    companion object {
        val CREATOR = SimpleJobCreator.forSupplier("sync") { SyncJob() }

        private val DEFAULT_SYNC_DELAY_MS = TimeUnit.MINUTES.toMillis(5)

        private val logger = LoggerFactory.getLogger("SyncJob")

        fun syncNow() {
            // start normal sync cycle now.
            scheduleNextSyncIn(0, TimeUnit.MILLISECONDS)
        }

        fun scheduleNextSync() {
            scheduleNextSyncIn(DEFAULT_SYNC_DELAY_MS, TimeUnit.MILLISECONDS)
        }

        fun scheduleNextSyncIn(delay: Long, unit: TimeUnit) {
            val delayInMilliseconds = unit.toMillis(delay)
            logger.info("Scheduling sync-job to run in {} seconds", delayInMilliseconds / 1000)

            val extras = PersistableBundleCompat()
            extras.putLong("delay", delayInMilliseconds)

            val builder = JobRequest.Builder("sync").apply {
                setUpdateCurrent(true)

                setExtras(extras)

                // in case of errors, just redo the job every 10 minutes
                setBackoffCriteria(TimeUnit.MINUTES.toMillis(10), JobRequest.BackoffPolicy.LINEAR)

                if (delayInMilliseconds == 0L) {
                    startNow()
                } else {
                    // start the job +/- 33% of the specified time.
                    val windowSize = delayInMilliseconds / 3
                    setExecutionWindow(delayInMilliseconds - windowSize, delayInMilliseconds + windowSize)

                    setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                }
            }

            doInBackground {
                JobManager.instance().schedule(builder.build())
            }
        }
    }
}
