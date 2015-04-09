package com.pr0gramm.app;

import android.app.Notification;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
            if(sync.isPresent() && sync.get().getInboxCount() > 0)
                showInboxNotification(sync.get());

            Log.i("SyncIntentService", "updating info");
            userService.info();

            // print info!
            Log.i("SyncIntentService", "finished without error after " + watch);

        } catch (Throwable thr) {
            Log.e(TAG, "Error while syncing", thr);

        } finally {
            SyncBroadcastReceiver.completeWakefulIntent(intent);
        }
    }

    private void showInboxNotification(Sync sync) {
        int iconId = R.drawable.ic_notify_unread;

        Bitmap icon = BitmapFactory.decodeResource(getResources(), iconId);

        int color = getResources().getColor(R.color.primary);
        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle("New message")
                .setContentText("Something happened on pr0gramm")
                .setSmallIcon(iconId)
                .setLargeIcon(icon)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .setLights(color, 500, 500)
                .build();

        NotificationManagerCompat.from(this).notify(ID_NEW_MESSAGE, notification);
    }
}
