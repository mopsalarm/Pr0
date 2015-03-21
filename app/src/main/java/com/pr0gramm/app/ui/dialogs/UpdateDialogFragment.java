package com.pr0gramm.app.ui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.inject.Inject;
import com.pr0gramm.app.DownloadCompleteReceiver;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.UpdateChecker;
import com.pr0gramm.app.ui.MainActionHandler;
import com.pr0gramm.app.ui.MainActivity;

import org.joda.time.Instant;

import roboguice.RoboGuice;
import roboguice.activity.RoboActionBarActivity;
import roboguice.activity.RoboFragmentActivity;
import roboguice.fragment.RoboDialogFragment;
import roboguice.inject.RoboInjector;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Actions;

import static org.joda.time.Duration.standardHours;
import static org.joda.time.Instant.now;
import static rx.android.observables.AndroidObservable.bindActivity;

/**
 */
public class UpdateDialogFragment extends RoboDialogFragment {
    public static final String KEY_DOWNLOAD_ID = "UpdateDialogFragment.downloadId";

    @Inject
    private DownloadManager downloadManager;

    @Inject
    private SharedPreferences sharedPreferences;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        UpdateChecker.Update update = getArguments().getParcelable("update");
        return new MaterialDialog.Builder(getActivity())
                .content(getString(R.string.new_update_available, update.getChangelog()))
                .positiveText(R.string.download)
                .negativeText(R.string.ignore)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        download(update);
                    }
                })
                .build();
    }

    private void download(UpdateChecker.Update update) {
        Uri apkUrl = Uri.parse(update.getApk());

        DownloadManager.Request request = new DownloadManager.Request(apkUrl)
                .setVisibleInDownloadsUi(false)
                .setTitle(apkUrl.getLastPathSegment());

        long downloadId = downloadManager.enqueue(request);
        sharedPreferences.edit()
                .putLong(KEY_DOWNLOAD_ID, downloadId)
                .apply();
    }

    public static UpdateDialogFragment newInstance(UpdateChecker.Update update) {
        Bundle bundle = new Bundle();
        bundle.putParcelable("update", update);

        UpdateDialogFragment dialog = new UpdateDialogFragment();
        dialog.setArguments(bundle);
        return dialog;
    }

    /**
     * Check for updates and shows a new {@link com.pr0gramm.app.ui.dialogs.UpdateDialogFragment}
     * if an update could be found.
     *
     * @param activity The activity that starts this update check.
     */
    public static void checkForUpdates(RoboActionBarActivity activity) {
        RoboInjector injector = RoboGuice.getInjector(activity);
        Settings settings = injector.getInstance(Settings.class);
        SharedPreferences shared = injector.getInstance(SharedPreferences.class);

        if (!settings.updateCheckEnabled())
            return;

        final String key = "UpdateDialogFragment.lastUpdateCheck";
        Instant last = new Instant(shared.getLong(key, 0));
        if (last.isAfter(now().minus(standardHours(1))))
            return;

        // Action to store the last check time
        Action0 storeCheckTime = () -> shared.edit().putLong(key, now().getMillis()).apply();

        // do the check
        bindActivity(activity, new UpdateChecker(activity).check())
                .onErrorResumeNext(Observable.empty())
                .finallyDo(storeCheckTime)
                .subscribe(update -> {
                    UpdateDialogFragment dialog = newInstance(update);
                    dialog.show(activity.getSupportFragmentManager(), null);
                }, Actions.empty());
    }
}
