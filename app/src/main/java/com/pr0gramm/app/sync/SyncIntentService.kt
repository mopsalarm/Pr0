package com.pr0gramm.app.sync

import android.app.IntentService
import android.content.Intent
import com.evernote.android.job.Job
import com.google.common.base.Stopwatch.createStarted
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Dagger
import com.pr0gramm.app.Settings
import com.pr0gramm.app.services.*
import com.pr0gramm.app.util.AndroidUtility.toOptional
import com.pr0gramm.app.util.ifPresent
import org.slf4j.LoggerFactory
import javax.inject.Inject

/**
 */
class SyncIntentService : IntentService(SyncIntentService::class.java.simpleName) {
    @Inject
    internal lateinit var userService: UserService

    @Inject
    internal lateinit var notificationService: NotificationService

    @Inject
    internal lateinit var singleShotService: SingleShotService

    @Inject
    internal lateinit var favedCommentService: FavedCommentService

    @Inject
    internal lateinit var settings: Settings

    override fun onHandleIntent(intent: Intent?) {
        Dagger.appComponent(this).inject(this)

        logger.info("Doing some statistics related trackings")
        if (singleShotService.firstTimeToday("track-settings:5"))
            Track.statistics()

        if (singleShotService.firstTimeToday("background-update-check") || BuildConfig.DEBUG) {
            toOptional(UpdateChecker(this).check()).ifPresent {
                notificationService.showUpdateNotification(it)
            }
        }

        if (singleShotService.firstTimeInHour("auto-sync-comments")) {
            logger.info("sync favorite comments")
            favedCommentService.updateCache()
        }

        logger.info("Performing a sync operation now")
        if (!userService.isAuthorized || intent == null)
            return

        val watch = createStarted()
        try {
            if (singleShotService.firstTimeToday("update-userInfo")) {
                logger.info("update current user info")
                userService.updateCachedUserInfo()
            }

            logger.info("performing sync")
            toOptional(userService.sync()).ifPresent { sync ->
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

        } finally {
            Job.completeWakefulIntent(intent)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("SyncIntentService")
    }
}
