package com.pr0gramm.app;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;

import com.afollestad.materialdialogs.MaterialDialog;

import rx.Observable;
import rx.Subscriber;

import static com.pr0gramm.app.AndroidUtility.checkMainThread;

/**
 */
public class BusyDialogFragment extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new MaterialDialog.Builder(getActivity())
                .content(R.string.please_wait)
                .progress(true, 0)
                .cancelable(false)
                .build();
    }

    private static class BusyDialogOperator<T> implements Observable.Operator<T, T> {
        private final String tag = "BusyDialog-" + System.identityHashCode(this);
        private final FragmentManager fragmentManager;

        public BusyDialogOperator(FragmentManager fragmentManager) {
            this.fragmentManager = fragmentManager;

            BusyDialogFragment dialog = new BusyDialogFragment();
            dialog.show(fragmentManager, tag);
        }

        private void dismiss() {
            checkMainThread();

            Fragment dialog = fragmentManager.findFragmentByTag(tag);
            if (dialog instanceof BusyDialogFragment)
                ((BusyDialogFragment) dialog).dismiss();
        }

        @Override
        public Subscriber<? super T> call(Subscriber<? super T> subscriber) {
            return new Subscriber<T>() {
                @Override
                public void onCompleted() {
                    try {
                        dismiss();
                    } catch (Throwable ignored) {
                    }

                    subscriber.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                    try {
                        dismiss();
                    } catch (Throwable ignored) {
                    }

                    subscriber.onError(e);
                }

                @Override
                public void onNext(T t) {
                    subscriber.onNext(t);
                }
            };
        }
    }

    static public <T> BusyDialogOperator<T> busyDialog(Fragment fragment) {
        return new BusyDialogOperator<>(fragment.getChildFragmentManager());
    }

    public static <T> BusyDialogOperator<T> busyDialog(FragmentActivity activity) {
        return new BusyDialogOperator<>(activity.getSupportFragmentManager());
    }
}
