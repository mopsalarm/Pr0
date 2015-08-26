package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.app.DownloadManager;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.google.inject.Inject;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.UpdateChecker;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.RxRoboAppCompatActivity;
import com.trello.rxlifecycle.ActivityEvent;

import org.joda.time.Instant;

import roboguice.RoboGuice;
import roboguice.fragment.RoboDialogFragment;
import roboguice.inject.RoboInjector;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Actions;

import static com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog;
import static org.joda.time.Duration.standardHours;
import static org.joda.time.Instant.now;

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

    private Dialog updateAvailableDialog(final UpdateChecker.Update update) {
        return DialogBuilder.start(getActivity())
                .content(getString(R.string.new_update_available, update.getChangelog()))
                .positive(R.string.download, () -> download(update))
                .negative(R.string.ignore)
                .build();
    }

    private Dialog noNewUpdateDialog() {
        return DialogBuilder.start(getActivity())
                .content(R.string.no_new_update)
                .positive(R.string.okay)
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
    public static void checkForUpdates(RxRoboAppCompatActivity activity, boolean interactive) {
        RoboInjector injector = RoboGuice.getInjector(activity);
        SharedPreferences shared = injector.getInstance(SharedPreferences.class);

        if (!interactive) {
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
        new UpdateChecker(activity).check()
                .onErrorResumeNext(Observable.empty())
                .defaultIfEmpty(null)
                .compose(activity.bindUntilEvent(ActivityEvent.DESTROY))
                .lift(busyOperator)
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
