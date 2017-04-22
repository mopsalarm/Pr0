package com.pr0gramm.app.ui;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import com.google.common.base.Throwables;
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment;
import com.pr0gramm.app.util.ErrorFormatting;

import java.lang.ref.WeakReference;

/**
 */
public class ActivityErrorHandler implements ErrorDialogFragment.OnErrorDialogHandler, Application.ActivityLifecycleCallbacks {
    private static final WeakReference<FragmentActivity> NULL = new WeakReference<FragmentActivity>(null);

    private WeakReference<FragmentActivity> current = NULL;

    private Throwable pendingError;
    private ErrorFormatting.Formatter pendingFormatter;

    public ActivityErrorHandler(Application application) {
        application.registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void showErrorDialog(Throwable error, ErrorFormatting.Formatter formatter) {
        FragmentActivity activity = current.get();
        if (activity != null) {
            String message = formatter.handles(error)
                    ? formatter.getMessage(activity, error)
                    : Throwables.getStackTraceAsString(error);

            if (message != null) {
                ErrorDialogFragment.Companion.showErrorString(activity.getSupportFragmentManager(), message);
            }

            // reset any pending errors
            pendingError = null;
            pendingFormatter = null;

        } else {
            this.pendingError = error;
            this.pendingFormatter = formatter;
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (activity instanceof FragmentActivity) {
            current = new WeakReference<FragmentActivity>((FragmentActivity) activity);

            if (pendingError != null && pendingFormatter != null) {
                showErrorDialog(pendingError, pendingFormatter);
            }
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (current.get() == activity) {
            current = NULL;
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        if (current.get() == activity) {
            current = NULL;
        }
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (current.get() == activity) {
            current = NULL;
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

}
