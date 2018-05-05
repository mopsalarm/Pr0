package com.pr0gramm.app.sync

import com.google.common.base.Stopwatch.createStarted
import com.pr0gramm.app.Stats
import com.pr0gramm.app.services.*
import com.pr0gramm.app.ui.dialogs.ignoreError
import org.slf4j.LoggerFactory


/**
 */
class SyncService(private val userService: UserService,
                  private val notificationService: NotificationService,
                  private val singleShotService: SingleShotService) {

    private val logger = LoggerFactory.getLogger("SyncService")

    fun syncStatistics() {
        Stats.get().incrementCounter("jobs.sync-stats")

        logger.info("Doing some statistics related trackings")
        Track.statistics()

        UpdateChecker().check().ignoreError().toBlocking().subscribe {
            notificationService.showUpdateNotification(it)
        }
    }

    fun sync() {
        Stats.get().incrementCounter("jobs.sync")

        if (!userService.isAuthorized) {
            logger.info("Will not sync now - user is not signed in.")
            return
        }

        logger.info("Performing a sync operation now")

        val watch = createStarted()
        try {
            if (singleShotService.firstTimeToday("update-userInfo")) {
                logger.info("update current user info")
                userService.updateCachedUserInfo()
            }

            logger.info("performing sync")
            userService.sync().ignoreError().toBlocking().subscribe { sync ->
                // print info!
                logger.info("finished without error after " + watch)

                // now show results, if any
                if (sync.inboxCount() > 0) {
                    notificationService.showForInbox(sync)
                } else {
                    // remove if no messages are found
                    notificationService.cancelForInbox()
                }
            }

        } catch (thr: Throwable) {
            logger.error("Error while syncing", thr)
        }
    }
}
