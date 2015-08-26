package com.pr0gramm.app.services;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.google.inject.Inject;
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment;

import java.io.File;

import roboguice.receiver.RoboBroadcastReceiver;

/**
 */
public class DownloadCompleteReceiver extends RoboBroadcastReceiver {
    @Inject
    private SharedPreferences sharedPreferences;

    @Inject
    private DownloadManager downloadManager;

    @SuppressLint("NewApi")
    @Override
    protected void handleReceive(Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null)
            return;

        long downloadId = extras.getLong(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        if (downloadId == -1)
            return;

        long expectedId = sharedPreferences.getLong(UpdateDialogFragment.KEY_DOWNLOAD_ID, -1);
        if (downloadId != expectedId)
            return;

        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = downloadManager.query(query)) {
            if (!cursor.moveToNext())
                return;

            int idx = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_FILENAME);
            String apk = cursor.getString(idx);
            if (apk == null)
                return;

            install(context, new File(apk));
        }

    }

    private void install(Context context, File apk) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
