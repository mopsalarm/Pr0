package com.pr0gramm.app.sync

import com.pr0gramm.app.Logger
import com.pr0gramm.app.Settings
import com.pr0gramm.app.Stats
import com.pr0gramm.app.services.*
import com.pr0gramm.app.time
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.util.catchAll
import com.pr0gramm.app.util.unless
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis
import kotlin.time.ExperimentalTime
import kotlin.time.seconds


/**
 */
@OptIn(ExperimentalTime::class)
class SyncService(private val userService: UserService,
                  private val notificationService: NotificationService,
                  private val singleShotService: SingleShotService,
                  private val seenService: SeenService,
                  private val kvService: KVService) {

    private val logger = Logger("SyncService")

    private val settings = Settings.get()
    private val seenSyncLock = AtomicBoolean()


    init {
        AsyncScope.launch {
            userService.loginStates
                    .mapNotNull { state -> state.uniqueToken }
                    .distinctUntilChanged()
                    .onStart { delay(1.seconds) }
                    .collect { token -> launch { performSyncSeenService(token) } }
        }
    }

    suspend fun dailySync() {
        Stats().incrementCounter("jobs.sync-stats")

        logger.info { "Doing some statistics related trackings" }
        Track.statistics()

        catchAll {
            UpdateChecker().queryAll().let { response ->
                if (response is UpdateChecker.Response.UpdateAvailable) {
                    notificationService.showUpdateNotification(response.update)
                }
            }
        }
    }

    suspend fun sync() {
        Stats().time("jobs.sync.time", measureTimeMillis {
            Stats().incrementCounter("jobs.sync")

            if (!userService.isAuthorized) {
                logger.info { "Will not sync now - user is not signed in." }
                return
            }

            logger.info { "Performing a sync operation now" }

            logger.time("Sync operation") {
                catchAll {
                    syncCachedUserInfo()
                }

                catchAll {
                    syncUserState()
                }

                catchAll {
                    syncSeenService()
                }
            }
        })
    }

    private suspend fun syncCachedUserInfo() {
        if (singleShotService.firstTimeToday("update-userInfo")) {
            logger.info { "Update current user info" }

            catchAll {
                userService.updateCachedUserInfo()
            }
        }
    }

    private suspend fun syncUserState() {
        logger.info { "Sync with pr0gramm api" }

        val sync = userService.sync() ?: return

        if (sync.inbox.total > 0) {
            notificationService.showUnreadMessagesNotification()
        } else {
            // remove if no messages are found
            notificationService.cancelForAllUnread()
        }
    }

    private suspend fun syncSeenService() {
        val shouldSync = settings.backup && if (settings.markItemsAsSeen) {
            singleShotService.firstTimeInHour("sync-seen")
        } else {
            singleShotService.firstTimeToday("sync-seen")
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

        logger.info { "Syncing of seen bits with token '$token'" }

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

        } catch (err: KVService.VersionConflictException) {
            // we should just retry.
            logger.warn(err) { "Version conflict during update." }

        } catch (err: Exception) {
            Stats().incrementCounter("seen.sync.error")
            throw err

        } finally {
            seenSyncLock.set(false)
        }
    }
}
