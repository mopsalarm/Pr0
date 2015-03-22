package com.pr0gramm.app;

import android.content.Intent;
import android.util.Log;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.pr0gramm.app.services.UserService;

import roboguice.service.RoboIntentService;

/**
 */
public class SyncIntentService extends RoboIntentService {
    private static final String TAG = "SyncIntentService";

    @Inject
    private UserService userService;

    public SyncIntentService() {
        super(SyncIntentService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(TAG, "Performing a sync operation now");
        if (!userService.isAuthorized())
            return;

        Stopwatch watch = Stopwatch.createStarted();
        try {
            Log.i("SyncIntentService", "performing sync");
            userService.sync();

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
}
