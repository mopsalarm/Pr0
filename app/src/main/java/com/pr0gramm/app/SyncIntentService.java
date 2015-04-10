package com.pr0gramm.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.pr0gramm.app.api.pr0gramm.response.Sync;
import com.pr0gramm.app.services.UserService;

import roboguice.service.RoboIntentService;

/**
 */
public class SyncIntentService extends RoboIntentService {
    private static final String TAG = "SyncIntentService";
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
        Log.i(TAG, "Performing a sync operation now");
        if (!userService.isAuthorized() || intent == null)
            return;

        Stopwatch watch = Stopwatch.createStarted();
        try {
            Log.i("SyncIntentService", "performing sync");
            Optional<Sync> sync = userService.sync();

            Log.i("SyncIntentService", "updating info");
            userService.info();

            // print info!
            Log.i("SyncIntentService", "finished without error after " + watch);

            // now show results, if any
            if (sync.isPresent() && sync.get().getInboxCount() > 0) {
                if (settings.showNotifications()) {
                    showInboxNotification(sync.get());
                }
            }

        } catch (Throwable thr) {
            Log.e(TAG, "Error while syncing", thr);

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


        String url = "http://pr0gramm.com/inbox/unread";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
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
