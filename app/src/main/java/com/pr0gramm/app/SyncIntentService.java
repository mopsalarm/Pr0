package com.pr0gramm.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.pr0gramm.app.api.pr0gramm.response.Sync;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.InboxActivity;
import com.pr0gramm.app.ui.InboxType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import roboguice.service.RoboIntentService;
import rx.functions.Actions;

import static com.pr0gramm.app.AndroidUtility.toOptional;

/**
 */
public class SyncIntentService extends RoboIntentService {
    private static final Logger logger = LoggerFactory.getLogger(SyncIntentService.class);

    private static final int ID_NEW_MESSAGE = 5001;

    @Inject
    private UserService userService;

    @Inject
    private Settings settings;

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
                if (settings.showNotifications()) {
                    showInboxNotification(sync.get());
                }
            }

        } catch (Throwable thr) {
            logger.error("Error while syncing", thr);

        } finally {
            SyncBroadcastReceiver.completeWakefulIntent(intent);
        }
    }

    private void showInboxNotification(Sync sync) {
        String title = sync.getInboxCount() == 1
                ? getString(R.string.notify_new_message_title)
                : getString(R.string.notify_new_messages_title);

        String content = sync.getInboxCount() == 1
                ? getString(R.string.notify_new_message_text)
                : getString(R.string.notify_new_messages_text, sync.getInboxCount());


        Intent intent = new Intent(this, InboxActivity.class);
        intent.putExtra(InboxActivity.EXTRA_INBOX_TYPE, InboxType.UNREAD.ordinal());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_notify_new_message)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .setLights(getResources().getColor(R.color.primary), 500, 500)
                .build();

        NotificationManagerCompat.from(this).notify(ID_NEW_MESSAGE, notification);
    }
}
