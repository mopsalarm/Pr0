package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.app.DownloadManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.Update;
import com.pr0gramm.app.services.UpdateChecker;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;
import com.pr0gramm.app.ui.base.BaseDialogFragment;
import com.trello.rxlifecycle.android.ActivityEvent;

import org.joda.time.Instant;

import javax.inject.Inject;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Actions;

import static com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog;
import static org.joda.time.Duration.standardHours;
import static org.joda.time.Instant.now;

/**
 */
public class UpdateDialogFragment extends BaseDialogFragment {
    @Inject
    DownloadManager downloadManager;

    @Inject
    SharedPreferences sharedPreferences;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Update update = getArguments().getParcelable("update");
        return update != null
                ? updateAvailableDialog(update)
                : noNewUpdateDialog();
    }

    private Dialog updateAvailableDialog(final Update update) {
        return DialogBuilder.start(getActivity())
                .content(getString(R.string.new_update_available, update.changelog()))
                .positive(R.string.download, () -> UpdateChecker.download(getActivity(), update))
                .negative(R.string.ignore)
                .build();
    }

    private Dialog noNewUpdateDialog() {
        return DialogBuilder.start(getActivity())
                .content(R.string.no_new_update)
                .positive()
                .build();
    }

    public static UpdateDialogFragment newInstance(Update update) {
        Bundle bundle = new Bundle();
        bundle.putParcelable("update", update);

        UpdateDialogFragment dialog = new UpdateDialogFragment();
        dialog.setArguments(bundle);
        return dialog;
    }

    /**
     * Check for updates and shows a new {@link UpdateDialogFragment}
     * if an update could be found.
     *
     * @param activity The activity that starts this update check.
     */
    public static void checkForUpdates(BaseAppCompatActivity activity, boolean interactive) {
        SharedPreferences shared = Dagger.appComponent(activity).sharedPreferences();

        if (!interactive && !BuildConfig.DEBUG) {
            Instant last = new Instant(shared.getLong(KEY_LAST_UPDATE_CHECK, 0));
            if (last.isAfter(now().minus(standardHours(1))))
                return;
        }

        // Action to store the last check time
        Action0 storeCheckTime = () -> shared.edit()
                .putLong(KEY_LAST_UPDATE_CHECK, now().getMillis())
                .apply();

        // show a busy-dialog or not?
        Observable.Operator<Update, Update> busyOperator =
                interactive ? busyDialog(activity) : NOOP;

        // do the check
        new UpdateChecker(activity).check()
                .onErrorResumeNext(Observable.empty())
                .defaultIfEmpty(null)
                .compose(activity.bindUntilEventAsync(ActivityEvent.DESTROY))
                .lift(busyOperator)
                .doAfterTerminate(storeCheckTime)
                .subscribe(update -> {
                    if (interactive || update != null) {
                        UpdateDialogFragment dialog = newInstance(update);
                        dialog.show(activity.getSupportFragmentManager(), null);
                    }
                }, Actions.empty());
    }

    private static final String KEY_LAST_UPDATE_CHECK = "UpdateDialogFragment.lastUpdateCheck";
    private static final Observable.Operator<Update, Update> NOOP = subscriber -> subscriber;

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }
}
