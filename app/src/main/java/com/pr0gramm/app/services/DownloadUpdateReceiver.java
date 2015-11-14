package com.pr0gramm.app.services;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.f2prateek.dart.Dart;
import com.f2prateek.dart.InjectExtra;
import com.pr0gramm.app.Dagger;

import javax.inject.Inject;

/**
 */
public class DownloadUpdateReceiver extends BroadcastReceiver {
    public static final String EXTRA_UPDATE = "DownloadUpdateReceiver.EXTRA_UPDATE";

    @Inject
    SharedPreferences sharedPreferences;

    @Inject
    DownloadManager downloadManager;

    @Inject
    NotificationService notificationService;

    @InjectExtra(EXTRA_UPDATE)
    Update update;

    @Override
    public void onReceive(Context context, Intent intent) {
        Dagger.appComponent(context).inject(this);
        Dart.inject(this, intent.getExtras());

        UpdateChecker.download(context, update);
    }
}
