package com.pr0gramm.app.ui;

import android.support.v4.app.FragmentActivity;

import com.google.common.base.Throwables;
import com.pr0gramm.app.ErrorFormatting;
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment;

/**
 */
public class ActivityErrorHandler implements ErrorDialogFragment.OnErrorDialogHandler {
    private final FragmentActivity activity;

    public ActivityErrorHandler(FragmentActivity activity) {
        this.activity = activity;
    }

    @Override
    public void showErrorDialog(Throwable error, ErrorFormatting.Formatter<?> formatter) {
        String message = formatter.handles(error)
                ? formatter.getMessage(activity, error)
                : Throwables.getStackTraceAsString(error);

        ErrorDialogFragment.showErrorString(activity.getSupportFragmentManager(), message);
    }
}
