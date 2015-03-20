package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;

import com.afollestad.materialdialogs.MaterialDialog;
import com.pr0gramm.app.ErrorFormatting;
import com.pr0gramm.app.R;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

import rx.Observable;
import rx.Subscriber;
import rx.plugins.RxJavaErrorHandler;
import rx.plugins.RxJavaPlugins;

import static com.pr0gramm.app.AndroidUtility.checkMainThread;
import static com.pr0gramm.app.AndroidUtility.logToCrashlytics;

/**
 * This dialog fragment shows and error to the user.
 * It also provides an {@link rx.Observable.Operator} using
 * the methods {@link #errorDialog()}
 * and {@link #errorDialog()}
 * that can be used with {@link rx.Observable} to catch errors.
 */
public class ErrorDialogFragment extends DialogFragment {
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

    public static void initRxErrorHandler() {
        checkMainThread();

        // we want to deliver the error to the main thread
        Handler mainHandler = new Handler(Looper.getMainLooper());

        // handle exceptions!
        RxJavaPlugins.getInstance().registerErrorHandler(new RxJavaErrorHandler() {
            @Override
            public void handleError(Throwable error) {
                try {
                    mainHandler.post(() -> processError(error, getGlobalErrorDialogHandler()));
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static class ErrorDialogOperator<T> implements Observable.Operator<T, T> {
        private final AtomicBoolean called = new AtomicBoolean();

        @Override
        public Subscriber<? super T> call(Subscriber<? super T> subscriber) {
            if (!called.compareAndSet(false, true))
                throw new UnsupportedOperationException("You can use this operator only once!");

            return new Subscriber<T>() {
                @Override
                public void onCompleted() {
                    try {
                        subscriber.onCompleted();

                    } catch (Throwable thr) {
                        processError(thr, getGlobalErrorDialogHandler());
                    }
                }

                @Override
                public void onError(Throwable err) {
                    processError(err, getGlobalErrorDialogHandler());

                    try {
                        subscriber.onCompleted();
                    } catch (Throwable thr) {
                        logToCrashlytics(err);
                    }
                }

                @Override
                public void onNext(T value) {
                    try {
                        subscriber.onNext(value);
                    } catch (Throwable thr) {
                        onError(thr);
                    }
                }
            };
        }
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    private static void processError(Throwable error, OnErrorDialogHandler handler) {
        Log.e("Error", "An error occurred", error);

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
        Log.i("Error", message);

        Bundle arguments = new Bundle();
        arguments.putString("content", message);

        // remove previous dialog, if any
        dismissErrorDialog(fragmentManager);

        ErrorDialogFragment dialog = new ErrorDialogFragment();
        dialog.setArguments(arguments);
        dialog.show(fragmentManager, "ErrorDialog");
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
            Log.w("Error", "Error removing previous dialog", error);
        }
    }

    /**
     * Creates a new error dialog operator.
     */
    public static <T> Observable.Operator<T, T> errorDialog() {
        return new ErrorDialogOperator<>();
    }
}
