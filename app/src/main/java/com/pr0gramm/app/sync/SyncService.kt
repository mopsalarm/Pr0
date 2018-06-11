package com.pr0gramm.app.sync

import com.google.common.base.Stopwatch.createStarted
import com.google.common.base.Throwables
import com.pr0gramm.app.Settings
import com.pr0gramm.app.Stats
import com.pr0gramm.app.services.*
import com.pr0gramm.app.ui.dialogs.ignoreError
import com.pr0gramm.app.util.*
import org.slf4j.LoggerFactory
import rx.Observable
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock


/**
 */
class SyncService(private val userService: UserService,
                  private val notificationService: NotificationService,
                  private val singleShotService: SingleShotService,
                  private val seenService: SeenService,
                  private val kvService: KVService) {

    private val logger = LoggerFactory.getLogger("SyncService")
    private val settings = Settings.get()

    init {

        // do a sync everytime the user token changes
        userService.loginState()
                .mapNotNull { state -> state.uniqueToken }
                .doOnNext { logger.info("Unique token is now {}", it) }
                .distinctUntilChanged()
                .delaySubscription(1, TimeUnit.SECONDS)
                .subscribe { syncSeenServiceAsync(it) }
    }

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

            if (settings.backup && singleShotService.firstTimeInHour("sync-seen")) {
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

    private val seenSyncLock = ReentrantLock()

    private fun syncSeenServiceAsync(token: String) = seenSyncLock.withTryLock {
        if (!settings.backup) {
            return
        }

        logger.info("Starting sync of seen bits.")

        // the first implementation of this feature had a bug where it would deflate
        // the stream again and by this wrongly set some of the lower random bytes.
        //
        // we will now just clear the lower bits
        //
        val fixObservable = if (singleShotService.isFirstTime("fix.clear-lower-seen-bits-2")) {
            kvService.get(token, "seen")
                    .ofType<KVService.GetResult.Value>()
                    .doOnNext { seenService.clearUpTo(it.value.size * 150 / 100) }
                    .ignoreError()

        } else {
            Observable.empty()
        }

        val updateObservable = kvService
                .update(token, "seen-bits") { previous ->
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

        fixObservable.ofType<KVService.PutResult.Version>()
                .concatWith(updateObservable)
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
