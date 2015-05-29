package com.pr0gramm.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.pr0gramm.app.services.UserService;

import org.joda.time.Duration;
import org.joda.time.Hours;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import roboguice.RoboGuice;

/**
 */
public class SyncBroadcastReceiver extends WakefulBroadcastReceiver {
    private static final Logger logger = LoggerFactory.getLogger(SyncBroadcastReceiver.class);

    @Override
    public void onReceive(Context context, Intent intent) {
        logger.info("System says, we shall sync now");
        UserService userService = RoboGuice.getInjector(context).getInstance(UserService.class);
        if (!userService.isAuthorized()) {
            logger.info("Looks like we are not authorized to sync.");
            return;
        }

        try {
            logger.info("Schedule another sync in one hour.");
            scheduleNextSync(context);
        } catch (Exception err) {
            logger.error("Could not schedule the next sync");
        }

        logger.info("Start the SyncIntentService now");
        Intent service = new Intent(context, SyncIntentService.class);
        startWakefulService(context, service);
    }

    public static void scheduleNextSync(Context context) {
        // we don't need to schedule, if benis graph is disabled.
        if (!Settings.of(context).benisGraphEnabled())
            return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // the intent to send to our app in one hour.
        Intent nextIntent = new Intent(context, SyncBroadcastReceiver.class);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0, nextIntent, 0);

        // register a pending event.
        Duration delay = Hours.ONE.toStandardDuration();
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delay.getMillis(), alarmIntent);
    }
}
