package com.pr0gramm.app.sync

import com.pr0gramm.app.Settings
import com.pr0gramm.app.Stats
import com.pr0gramm.app.services.*
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.ui.base.await
import com.pr0gramm.app.ui.fragments.IndicatorStyle
import com.pr0gramm.app.util.*
import kotlinx.coroutines.launch
import rx.lang.kotlin.onErrorReturnNull
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
                .doOnNext { logger.debug { "Unique token is now $it" } }
                .delaySubscription(1, TimeUnit.SECONDS)
                .subscribe { AsyncScope.launch { performSyncSeenService(it) } }
    }

    suspend fun syncStatistics() {
        Stats.get().incrementCounter("jobs.sync-stats")

        logger.info { "Doing some statistics related trackings" }
        Track.statistics()

        UpdateChecker().queryAll().let { response ->
            if (response is UpdateChecker.Response.UpdateAvailable) {
                notificationService.showUpdateNotification(response.update)
            }
        }
    }

    suspend fun sync() {
        Stats.get().time("jobs.sync.time", measureTimeMillis {
            Stats.get().incrementCounter("jobs.sync")

            if (!userService.isAuthorized) {
                logger.info { "Will not sync now - user is not signed in." }
                return
            }

            logger.info { "Performing a sync operation now" }

            logger.time("Sync operation") {
                ignoreException {
                    syncCachedUserInfo()
                }

                ignoreException {
                    syncUserState()
                }

                ignoreException {
                    syncSeenService()
                }
            }
        })
    }

    private suspend fun syncCachedUserInfo() {
        if (singleShotService.firstTimeToday("update-userInfo")) {
            logger.info { "Update current user info" }
            userService.updateCachedUserInfo().onErrorReturnNull().await()
        }
    }

    private suspend fun syncUserState() {
        logger.info { "Sync with pr0gramm api" }

        val sync = userService.sync() ?: return

        if (sync.inboxCount > 0) {
            notificationService.showUnreadMessagesNotification()
        } else {
            // remove if no messages are found
            notificationService.cancelForInbox()
        }
    }

    private suspend fun syncSeenService() {
        val shouldSync = settings.backup && when (settings.seenIndicatorStyle) {
            IndicatorStyle.NONE -> singleShotService.firstTimeToday("sync-seen")
            else -> singleShotService.firstTimeInHour("sync-seen")
        }

        val token = userService.loginState.uniqueToken
        if (shouldSync && token != null) {
            performSyncSeenService(token)
        }
    }

    private suspend fun performSyncSeenService(token: String) {
        unless(settings.backup && seenSyncLock.compareAndSet(false, true)) {
            logger.info { "Not starting sync of seen bits." }
            return
        }

        logger.info { "Syncing of seen bits." }

        try {
            kvService.update(token, "seen-bits") { previous ->
                // merge the previous state into the current seen service
                val noChanges = previous != null && seenService.checkEqualAndMerge(previous)

                if (noChanges) {
                    logger.info { "No seen bits changed, so wont push now" }
                    null
                } else {
                    logger.info { "Seen bits look dirty, pushing now" }
                    seenService.export().takeIf { it.isNotEmpty() }
                }
            }

        } catch (err: Exception) {
            Stats.get().incrementCounter("seen.sync.error")
            throw err

        } finally {
            seenSyncLock.set(false)
        }
    }
}
