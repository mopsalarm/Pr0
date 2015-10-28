package com.pr0gramm.app.sync;

import android.app.IntentService;
import android.content.Intent;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.api.pr0gramm.response.Sync;
import com.pr0gramm.app.services.NotificationService;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.services.UserService;

import org.slf4j.Logger;

import javax.inject.Inject;

import static android.support.v4.content.WakefulBroadcastReceiver.completeWakefulIntent;
import static com.google.common.base.Stopwatch.createStarted;
import static com.pr0gramm.app.services.Track.statistics;
import static com.pr0gramm.app.util.AndroidUtility.toOptional;
import static org.slf4j.LoggerFactory.getLogger;

/**
 */
public class SyncIntentService extends IntentService {
    private static final Logger logger = getLogger(SyncIntentService.class);

    @Inject
    UserService userService;

    @Inject
    NotificationService notificationService;

    @Inject
    SingleShotService singleShotService;

    @Inject
    Settings settings;

    public SyncIntentService() {
        super(SyncIntentService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Dagger.appComponent(this).inject(this);

        logger.info("Doing some statistics related trackings");
        if (singleShotService.isFirstTimeToday("track-settings"))
            statistics(settings, userService.isAuthorized());

        logger.info("Performing a sync operation now");
        if (!userService.isAuthorized() || intent == null)
            return;

        Stopwatch watch = createStarted();
        try {
            logger.info("performing sync");
            Optional<Sync> sync = toOptional(userService.sync());

            logger.info("updating info");
            toOptional(userService.info());

            // print info!
            logger.info("finished without error after " + watch);

            // now show results, if any
            if (sync.isPresent()) {
                if (sync.get().getInboxCount() > 0) {
                    notificationService.showForInbox(sync.get());
                } else {
                    // remove if no messages are found
                    notificationService.cancelForInbox();
                }
            }

        } catch (Throwable thr) {
            logger.error("Error while syncing", thr);

        } finally {
            completeWakefulIntent(intent);
        }
    }
}
