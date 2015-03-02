package com.pr0gramm.app;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;

import com.afollestad.materialdialogs.MaterialDialog;
import com.crashlytics.android.Crashlytics;

import rx.Observable;
import rx.Subscriber;

/**
 */
public class ErrorDialogFragment extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        return new MaterialDialog.Builder(getActivity())
                .content(args.getString("content"))
                .positiveText(R.string.okay)
                .build();
    }

    public static class ErrorDialogOperator<T> implements Observable.Operator<T, T> {
        private final FragmentManager fragmentManager;

        public ErrorDialogOperator(FragmentManager fragmentManager) {
            this.fragmentManager = fragmentManager;
        }

        @Override
        public Subscriber<? super T> call(Subscriber<? super T> subscriber) {
            return new Subscriber<T>() {
                @Override
                public void onCompleted() {
                    subscriber.onCompleted();
                }

                @Override
                public void onError(Throwable err) {
                    try {
                        show(fragmentManager, err);
                    } catch (Throwable thr) {
                        Crashlytics.logException(err);
                    }

                    onCompleted();
                }

                @Override
                public void onNext(T t) {
                    subscriber.onNext(t);
                }
            };
        }
    }

    private static void show(FragmentManager fragmentManager, Throwable err) {
        String message = err.getLocalizedMessage();
        if (message == null)
            message = err.toString();

        Crashlytics.logException(err);
        showErrorString(fragmentManager, message);
    }

    public static void showErrorString(FragmentManager fragmentManager, String message) {
        Bundle arguments = new Bundle();
        arguments.putString("content", message);

        ErrorDialogFragment dialog = new ErrorDialogFragment();
        dialog.setArguments(arguments);
        dialog.show(fragmentManager, (String) null);
    }

    public static <T> ErrorDialogOperator<T> errorDialog(Fragment fragment) {
        return new ErrorDialogOperator<>(fragment.getChildFragmentManager());
    }

    public static <T> ErrorDialogOperator<T> errorDialog(FragmentActivity activity) {
        return new ErrorDialogOperator<>(activity.getSupportFragmentManager());
    }
}
