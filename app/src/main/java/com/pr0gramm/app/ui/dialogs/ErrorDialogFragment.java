package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import com.afollestad.materialdialogs.MaterialDialog;
import com.pr0gramm.app.ErrorFormatting;
import com.pr0gramm.app.R;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;

import rx.functions.Action1;

import static com.pr0gramm.app.AndroidUtility.checkMainThread;
import static com.pr0gramm.app.AndroidUtility.logToCrashlytics;

/**
 * This dialog fragment shows and error to the user.
 */
public class ErrorDialogFragment extends DialogFragment {
    private static final Logger logger = LoggerFactory.getLogger(ErrorDialogFragment.class);

    private static WeakReference<OnErrorDialogHandler> GLOBAL_ERROR_DIALOG_HANDLER;
    private static WeakReference<Throwable> PREVIOUS_ERROR = new WeakReference<>(null);

    public interface OnErrorDialogHandler {
        /**
         */
        void showErrorDialog(Throwable error, ErrorFormatting.Formatter<?> formatter);
    }

    public static void setGlobalErrorDialogHandler(OnErrorDialogHandler handler) {
        checkMainThread();
        GLOBAL_ERROR_DIALOG_HANDLER = new WeakReference<>(handler);
    }

    public static void unsetGlobalErrorDialogHandler(OnErrorDialogHandler handler) {
        checkMainThread();

        if (GLOBAL_ERROR_DIALOG_HANDLER != null) {
            OnErrorDialogHandler oldHandler = GLOBAL_ERROR_DIALOG_HANDLER.get();
            if (oldHandler == handler)
                GLOBAL_ERROR_DIALOG_HANDLER = null;
        }
    }

    public static OnErrorDialogHandler getGlobalErrorDialogHandler() {
        if (GLOBAL_ERROR_DIALOG_HANDLER == null)
            return null;

        return GLOBAL_ERROR_DIALOG_HANDLER.get();
    }

//    public static void initRxErrorHandler() {
//        checkMainThread();
//
//        // we want to deliver the error to the main thread
//        Handler mainHandler = new Handler(Looper.getMainLooper());
//
//        // handle exceptions!
//        RxJavaPlugins.getInstance().registerErrorHandler(new RxJavaErrorHandler() {
//            @Override
//            public void handleError(Throwable error) {
//                // only handle errors that no one else handled
//                if (!(error instanceof OnErrorNotImplementedException))
//                    return;
//
//                // get the cause
//                Throwable cause = error.getCause();
//
//                logger.info("The next error occurred somewhere in RxJava");
//                try {
//                    mainHandler.post(() -> processError(cause, getGlobalErrorDialogHandler()));
//                } catch (Throwable ignored) {
//                }
//            }
//        });
//    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    private static void processError(Throwable error, OnErrorDialogHandler handler) {
        logger.error("An error occurred", error);

        try {
            // do some checking so we don't log this exception twice
            boolean sendToCrashlytics = PREVIOUS_ERROR.get() != error;
            PREVIOUS_ERROR = new WeakReference<>(error);

            // format and log
            ErrorFormatting.Formatter<?> formatter = ErrorFormatting.getFormatter(error);
            if (sendToCrashlytics && formatter.shouldSendToCrashlytics())
                logToCrashlytics(error);

            if (handler != null) {
                handler.showErrorDialog(error, formatter);
            }

        } catch (Throwable thr) {
            // there was an error handling the error. oops.
            logToCrashlytics(thr);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        return new MaterialDialog.Builder(getActivity())
                .content(args.getString("content"))
                .positiveText(R.string.okay)
                .build();
    }

    public static void showErrorString(FragmentManager fragmentManager, String message) {
        logger.info(message);

        try {
            Bundle arguments = new Bundle();
            arguments.putString("content", message);

            // remove previous dialog, if any
            dismissErrorDialog(fragmentManager);

            ErrorDialogFragment dialog = new ErrorDialogFragment();
            dialog.setArguments(arguments);
            dialog.show(fragmentManager, "ErrorDialog");

        } catch(Exception error) {
            logger.error("Could not show error dialog", error);
        }
    }

    /**
     * Dismisses any previously shown error dialog.
     */
    private static void dismissErrorDialog(FragmentManager fm) {
        try {
            Fragment previousFragment = fm.findFragmentByTag("ErrorDialog");
            if (previousFragment instanceof DialogFragment) {
                DialogFragment dialog = (DialogFragment) previousFragment;
                dialog.dismissAllowingStateLoss();
            }

        } catch (Throwable error) {
            logger.warn("Error removing previous dialog", error);
        }
    }

    /**
     * Creates the default error callback {@link rx.functions.Action1}
     */
    public static Action1<Throwable> defaultOnError() {
        return error -> processError(error, getGlobalErrorDialogHandler());
    }
}
