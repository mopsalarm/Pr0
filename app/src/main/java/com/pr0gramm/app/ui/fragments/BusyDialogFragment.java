package com.pr0gramm.app.ui.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.pr0gramm.app.DialogBuilder;
import com.pr0gramm.app.R;
import com.pr0gramm.app.ui.MaterialProgressDrawable;

import rx.Observable;
import rx.Subscriber;

import static com.pr0gramm.app.AndroidUtility.checkMainThread;

/**
 */
public class BusyDialogFragment extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.progress_dialog, null);

        TextView text = (TextView) view.findViewById(R.id.text);
        text.setText(getDialogText());

        ImageView image = (ImageView) view.findViewById(R.id.image);
        MaterialProgressDrawable mpd = new MaterialProgressDrawable(getActivity(), image);
        mpd.setColorSchemeColors(getResources().getColor(R.color.primary));
        mpd.setBackgroundColor(getResources().getColor(android.R.color.transparent));
        mpd.updateSizes(MaterialProgressDrawable.LARGE);
        mpd.setAlpha(255);

        return DialogBuilder.start(getActivity())
                .content(view)
                .cancelable(false)
                .onShow(di -> {
                    image.setImageDrawable(mpd);
                    mpd.start();
                })
                .build();
    }

    private String getDialogText() {
        Bundle args = getArguments();
        if (args != null) {
            String text = args.getString("text");
            if (text != null)
                return text;
        }
        return getString(R.string.please_wait);
    }

    private static class BusyDialogOperator<T> implements Observable.Operator<T, T> {
        private final String tag = "BusyDialog-" + System.identityHashCode(this);
        private final FragmentManager fragmentManager;

        public BusyDialogOperator(FragmentManager fragmentManager, String text) {
            this.fragmentManager = fragmentManager;

            BusyDialogFragment dialog = new BusyDialogFragment();
            if (text != null) {
                Bundle args = new Bundle();
                args.putString("text", text);
                dialog.setArguments(args);
            }
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
        return new BusyDialogOperator<>(fragment.getChildFragmentManager(), null);
    }

    static public <T> BusyDialogOperator<T> busyDialog(Fragment fragment, String text) {
        return new BusyDialogOperator<>(fragment.getChildFragmentManager(), text);
    }

    public static <T> BusyDialogOperator<T> busyDialog(FragmentActivity activity) {
        return new BusyDialogOperator<>(activity.getSupportFragmentManager(), null);
    }
}
