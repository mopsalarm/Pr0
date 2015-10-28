package com.pr0gramm.app.services;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment;

import java.io.File;

import javax.inject.Inject;

/**
 */
public class DownloadCompleteReceiver extends BroadcastReceiver {
    @Inject
    SharedPreferences sharedPreferences;

    @Inject
    DownloadManager downloadManager;

    @SuppressLint("NewApi")
    @Override
    public void onReceive(Context context, Intent intent) {
        Dagger.appComponent(context).inject(this);

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
