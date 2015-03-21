package com.pr0gramm.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import com.pr0gramm.app.services.UserService;

import roboguice.RoboGuice;

/**
 */
public class SyncBroadcastReceiver extends WakefulBroadcastReceiver {
    private static final String TAG = "SyncBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "System says, we shall sync now");
        UserService userService = RoboGuice.getInjector(context).getInstance(UserService.class);
        if (!userService.isAuthorized()) {
            Log.i(TAG, "Looks like we are not authorized to sync.");
            return;
        }

        try {
            Log.i(TAG, "Schedule another sync in one hour.");
            scheduleNextSync(context);
        } catch (Exception err) {
            Log.e(TAG, "Could not schedule the next sync");
        }

        Log.i(TAG, "Start the SyncIntentService now");
        Intent service = new Intent(context, SyncIntentService.class);
        startWakefulService(context, service);
    }

    public static void scheduleNextSync(Context context) {
        // we dont need to schedule, if benis graph is disabled.
        if (!Settings.of(context).benisGraphEnabled())
            return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // the intent to send to our app in one hour.
        Intent nextIntent = new Intent(context, SyncBroadcastReceiver.class);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0, nextIntent, 0);

        // register a pending event.
        int oneHour = 3600 * 1000;
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + oneHour, alarmIntent);
    }
}
