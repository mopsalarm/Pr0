package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.app.DownloadManager;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.inject.Inject;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.UpdateChecker;

import org.joda.time.Instant;

import roboguice.RoboGuice;
import roboguice.activity.RoboActionBarActivity;
import roboguice.fragment.RoboDialogFragment;
import roboguice.inject.RoboInjector;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Actions;

import static com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog;
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
        return update != null
                ? updateAvailableDialog(update)
                : noNewUpdateDialog();
    }

    private MaterialDialog updateAvailableDialog(final UpdateChecker.Update update) {
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

    private Dialog noNewUpdateDialog() {
        return new MaterialDialog.Builder(getActivity())
                .content(R.string.no_new_update)
                .positiveText(R.string.okay)
                .show();
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
    public static void checkForUpdates(RoboActionBarActivity activity, boolean interactive) {
        RoboInjector injector = RoboGuice.getInjector(activity);
        Settings settings = injector.getInstance(Settings.class);
        SharedPreferences shared = injector.getInstance(SharedPreferences.class);

        if (!interactive) {
            if (!settings.updateCheckEnabled())
                return;

            Instant last = new Instant(shared.getLong(KEY_LAST_UPDATE_CHECK, 0));
            if (last.isAfter(now().minus(standardHours(1))))
                return;
        }

        // Action to store the last check time
        Action0 storeCheckTime = () -> shared.edit()
                .putLong(KEY_LAST_UPDATE_CHECK, now().getMillis())
                .apply();

        // show a busy-dialog or not?
        Observable.Operator<UpdateChecker.Update, UpdateChecker.Update> busyOperator =
                interactive ? busyDialog(activity) : NOOP;

        // do the check
        bindActivity(activity, new UpdateChecker(activity).check())
                .onErrorResumeNext(Observable.empty())
                .lift(busyOperator)
                .defaultIfEmpty(null)
                .finallyDo(storeCheckTime)
                .subscribe(update -> {
                    if (interactive || update != null) {
                        UpdateDialogFragment dialog = newInstance(update);
                        dialog.show(activity.getSupportFragmentManager(), null);
                    }
                }, Actions.empty());
    }

    private static final String KEY_LAST_UPDATE_CHECK = "UpdateDialogFragment.lastUpdateCheck";
    private static final Observable.Operator<UpdateChecker.Update, UpdateChecker.Update> NOOP = subscriber -> subscriber;
}
