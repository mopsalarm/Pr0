package com.pr0gramm.app;

import android.content.Intent;
import android.util.Log;

import com.google.inject.Inject;
import com.pr0gramm.app.api.pr0gramm.response.Sync;
import com.pr0gramm.app.services.UserService;

import java.util.concurrent.TimeUnit;

import roboguice.service.RoboIntentService;
import rx.Observable;

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

        try {
            // perform a sync
            userService.sync()
                    .timeout(25, TimeUnit.SECONDS)
                    .onErrorResumeNext(Observable.<Sync>empty())
                    .toBlocking()
                    .firstOrDefault(null);

        } finally {
            SyncBroadcastReceiver.completeWakefulIntent(intent);
        }
    }
}
