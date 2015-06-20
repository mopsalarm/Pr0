package com.pr0gramm.app;

import android.content.Intent;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.pr0gramm.app.api.pr0gramm.response.Sync;
import com.pr0gramm.app.services.UserService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import roboguice.service.RoboIntentService;

import static com.pr0gramm.app.AndroidUtility.toOptional;

/**
 */
public class SyncIntentService extends RoboIntentService {
    private static final Logger logger = LoggerFactory.getLogger(SyncIntentService.class);

    @Inject
    private UserService userService;

    @Inject
    private NotificationService notificationService;

    public SyncIntentService() {
        super(SyncIntentService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        logger.info("Performing a sync operation now");
        if (!userService.isAuthorized() || intent == null)
            return;

        Stopwatch watch = Stopwatch.createStarted();
        try {
            logger.info("performing sync");
            Optional<Sync> sync = toOptional(userService.sync());

            logger.info("updating info");
            toOptional(userService.info());

            // print info!
            logger.info("finished without error after " + watch);

            // now show results, if any
            if (sync.isPresent() && sync.get().getInboxCount() > 0) {
                notificationService.showForInbox(sync.get());
            }

        } catch (Throwable thr) {
            logger.error("Error while syncing", thr);

        } finally {
            SyncBroadcastReceiver.completeWakefulIntent(intent);
        }
    }

}
