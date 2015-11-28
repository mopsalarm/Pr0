package com.pr0gramm.app.sync;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.services.UserService;

import org.joda.time.Hours;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.joda.time.Duration.standardHours;
import static org.joda.time.Duration.standardMinutes;

/**
 */
public class SyncBroadcastReceiver extends WakefulBroadcastReceiver {
    private static final Logger logger = LoggerFactory.getLogger("SyncBroadcastReceiver");

    private static final long DEFAULT_SYNC_DELAY = standardMinutes(5).getMillis();

    @Override
    public void onReceive(Context context, Intent intent) {
        logger.info("System says, we shall sync now");
        UserService userService = Dagger.appComponent(context).userService();

        long syncTime;
        if (!userService.isAuthorized()) {
            logger.info("We are not logged in, lets schedule the next sync in 6 hours");
            syncTime = SystemClock.elapsedRealtime() + standardHours(6).getMillis();
        } else {
            syncTime = getNextSyncTime(context);
        }

        try {
            logger.info("Scheduling next sync now");
            scheduleNextSync(context, syncTime);
        } catch (Exception err) {
            logger.error("Could not schedule the next sync");
        }

        logger.info("Start the SyncIntentService now");
        Intent service = new Intent(context, SyncIntentService.class);
        startWakefulService(context, service);
    }

    private static void scheduleNextSync(Context context, long syncTime) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // the intent to send to our app in one hour.
        Intent nextIntent = new Intent(context, SyncBroadcastReceiver.class);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0, nextIntent, 0);

        // register a pending event.
        logger.info("Schedule another sync");
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, syncTime, alarmIntent);
    }

    public static void syncNow(Context context) {
        Intent intent = new Intent(context, SyncBroadcastReceiver.class);
        context.sendBroadcast(intent);

        getSyncPrefs(context).edit().putLong("delay", DEFAULT_SYNC_DELAY).apply();
    }

    private static long getNextSyncTime(Context context) {
        SharedPreferences prefs = getSyncPrefs(context);

        long delay = Math.min(
                prefs.getLong("delay", DEFAULT_SYNC_DELAY),
                Hours.ONE.toStandardDuration().getMillis());

        logger.info("sync delay is now {} minutes", delay / (60 * 1000));

        prefs.edit().putLong("delay", 2 * delay).apply();
        return SystemClock.elapsedRealtime() + delay;
    }

    public static void scheduleNextSync(Context context) {
        scheduleNextSync(context, getNextSyncTime(context));
    }

    private static SharedPreferences getSyncPrefs(Context context) {
        return context.getSharedPreferences("sync", Context.MODE_PRIVATE);
    }
}
