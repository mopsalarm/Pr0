package com.pr0gramm.app.sync

import com.pr0gramm.app.Settings
import com.pr0gramm.app.Stats
import com.pr0gramm.app.services.*
import com.pr0gramm.app.ui.dialogs.ignoreError
import com.pr0gramm.app.ui.fragments.IndicatorStyle
import com.pr0gramm.app.util.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis


/**
 */
class SyncService(private val userService: UserService,
                  private val notificationService: NotificationService,
                  private val singleShotService: SingleShotService,
                  private val seenService: SeenService,
                  private val kvService: KVService) {

    private val logger = logger("SyncService")

    private val settings = Settings.get()
    private val seenSyncLock = AtomicBoolean()


    init {
        // do a sync every time the user token changes
        userService.loginStates
                .mapNotNull { state -> state.uniqueToken }
                .distinctUntilChanged()
                .doOnNext { logger.debug("Unique token is now {}", it) }
                .delaySubscription(1, TimeUnit.SECONDS)
                .subscribe { performSyncSeenService(it) }
    }

    fun syncStatistics() {
        Stats.get().incrementCounter("jobs.sync-stats")

        logger.info("Doing some statistics related trackings")
        Track.statistics()

        UpdateChecker().check().ignoreError().subscribe {
            notificationService.showUpdateNotification(it)
        }
    }

    fun sync() {
        Stats.get().time("jobs.sync.time", measureTimeMillis {
            Stats.get().incrementCounter("jobs.sync")

            if (!userService.isAuthorized) {
                logger.info("Will not sync now - user is not signed in.")
                return
            }

            logger.info("Performing a sync operation now")

            logger.time("Sync operation") {
                try {
                    syncCachedUserInfo()
                    syncUserState()
                    syncSeenService()

                } catch (thr: Throwable) {
                    logger.error("Error while syncing", thr)
                }
            }
        })
    }

    private fun syncCachedUserInfo() {
        if (singleShotService.firstTimeToday("update-userInfo")) {
            logger.info("Update current user info")
            userService.updateCachedUserInfo()
                    .ignoreError().subscribe()
        }
    }

    private fun syncUserState() {
        logger.info("Sync with pr0gramm api")

        userService.sync().ignoreError().subscribe { sync ->
            // now show results, if any
            if (sync.inboxCount > 0) {
                notificationService.showForInbox(sync)
            } else {
                // remove if no messages are found
                notificationService.cancelForInbox()
            }
        }
    }

    private fun syncSeenService() {
        val shouldSync = settings.backup && when (settings.seenIndicatorStyle) {
            IndicatorStyle.NONE -> singleShotService.firstTimeToday("sync-seen")
            else -> singleShotService.firstTimeInHour("sync-seen")
        }

        val token = userService.loginState.uniqueToken
        if (shouldSync && token != null) {
            performSyncSeenService(token)
        }
    }

    private fun performSyncSeenService(token: String) {
        unless(settings.backup && seenSyncLock.compareAndSet(false, true)) {
            logger.info("Not starting sync of seen bits.")
            return
        }

        logger.info("Syncing of seen bits.")

        kvService
                .update(token, "seen-bits") { previous ->
                    // merge the previous state into the current seen service
                    val noChanges = previous != null && seenService.checkEqualAndMerge(previous)

                    if (noChanges) {
                        logger.info("No seen bits changed, so wont push now")
                        null
                    } else {
                        logger.info("Seen bits look dirty, pushing now")
                        seenService.export().takeIf { it.isNotEmpty() }
                    }
                }

                .doOnError { err ->
                    Stats.get().incrementCounter("seen.sync.error")

                    // log non IOExceptions
                    if (err.causalChain.all { it !is IOException }) {
                        AndroidUtility.logToCrashlytics(err)
                    }
                }

                .ignoreError()

                .doAfterTerminate { seenSyncLock.set(false) }
                .subscribe()
    }
}
