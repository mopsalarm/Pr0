package com.pr0gramm.app;

import android.content.Context;
import android.content.Intent;
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

        Log.i(TAG, "Start the SyncIntentService now");
        Intent service = new Intent(context, SyncIntentService.class);
        startWakefulService(context, service);
    }
}
