package com.pr0gramm.app.sync

import com.google.common.base.Stopwatch.createStarted
import com.google.common.base.Throwables
import com.pr0gramm.app.Stats
import com.pr0gramm.app.services.*
import com.pr0gramm.app.ui.dialogs.ignoreError
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.subscribeOnBackground
import org.slf4j.LoggerFactory
import java.io.IOException


/**
 */
class SyncService(private val userService: UserService,
                  private val notificationService: NotificationService,
                  private val singleShotService: SingleShotService,
                  private val seenService: SeenService,
                  private val kvService: KVService) {

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

            if (singleShotService.firstTimeInHour("sync-seen")) {
                userService.loginState().take(1).subscribe { state ->
                    if (state.uniqueToken != null) {
                        syncSeenServiceAsync(state.uniqueToken)
                    }
                }
            }

            logger.info("performing sync")
            userService.sync().ignoreError().toBlocking().subscribe { sync ->
                // print info!
                logger.info("finished without error after {}", watch)

                // now show results, if any
                if (sync.inboxCount > 0) {
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

    private fun syncSeenServiceAsync(token: String) {
        val updateObservable = kvService
                .update(token, "seen") { previous ->
                    if (previous != null) {
                        // merge the previous state into the current seen service
                        seenService.merge(previous)
                    }

                    // only upload if dirty, and if the export is not empty.
                    if (seenService.dirty) {
                        seenService.export().takeIf { it.isNotEmpty() }
                    } else {
                        null
                    }
                }

        updateObservable
                .doOnError { err ->
                    Stats.get().incrementCounter("seen.sync.error")

                    // log non IOExceptions
                    if (Throwables.getCausalChain(err).all { it !is IOException }) {
                        AndroidUtility.logToCrashlytics(err)
                    }
                }

                .ignoreError()
                .subscribeOnBackground()
                .subscribe()
    }
}
