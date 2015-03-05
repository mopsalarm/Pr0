package com.pr0gramm.app;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;

import com.afollestad.materialdialogs.MaterialDialog;
import com.crashlytics.android.Crashlytics;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import org.joda.time.Instant;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import rx.Observable;
import rx.Subscriber;

/**
 * This dialog fragment shows and error to the user.
 * It also provides an {@link rx.Observable.Operator} using
 * the methods {@link #errorDialog(android.support.v4.app.Fragment)}
 * and {@link #errorDialog(android.support.v4.app.FragmentActivity)}
 * that can be used with {@link rx.Observable} to catch errors.
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

    /**
     * This is a bit tricky. If we start a background task and rotate the
     * activity, the activity (and many of its fragments) will be rebuild and all references
     * to those fragments will be invalid. The other way around, all references to the
     * background task will be destroyed too.
     * <p>
     * Because of this, we add a {@link com.pr0gramm.app.ErrorDialogFragment.HelperFragment}
     * with a unique tag. This fragment will be recreated on orientation change.
     * The fragment put its name into some static cache, so that the background task
     * can get a reference to the fragment knowing its tag. This reference can then be used
     * to get a fragment manager to show the error dialog.
     */
    public static class HelperFragment extends Fragment {
        public String getName() {
            return getArguments().getString("name");
        }

        @Override
        public void onResume() {
            super.onResume();
            CACHE.put(getName(), this);
        }

        @Override
        public void onPause() {
            CACHE.invalidate(getName());
            super.onPause();
        }

        public static HelperFragment newInstance() {
            Bundle bundle = new Bundle();
            bundle.putString("name", UUID.randomUUID().toString());

            HelperFragment fragment = new HelperFragment();
            fragment.setArguments(bundle);
            return fragment;
        }

        private static final Cache<String, HelperFragment> CACHE
                = CacheBuilder.newBuilder().weakValues().build();
    }

    private static class ErrorDialogOperator<T> implements Observable.Operator<T, T> {
        private final AtomicBoolean called = new AtomicBoolean();
        private final String fragmentTag;

        private ErrorDialogOperator(FragmentManager parentFragmentManager) {
            HelperFragment fragment = HelperFragment.newInstance();
            fragmentTag = fragment.getName();

            parentFragmentManager.beginTransaction()
                    .add(fragment, fragmentTag)
                    .commitAllowingStateLoss();
        }

        private void removeHelperFragment() {
            HelperFragment fragment = HelperFragment.CACHE.getIfPresent(fragmentTag);
            if (fragment == null || fragment.isDetached()) {
                HelperFragment.CACHE.invalidate(fragmentTag);
                return;
            }

            // remove fragment now.
            fragment.getFragmentManager().beginTransaction()
                    .remove(fragment)
                    .commitAllowingStateLoss();
        }

        /**
         * Gets the {@link com.pr0gramm.app.ErrorDialogFragment.HelperFragment} and uses
         * its fragment manager to show the dialog.
         *
         * @param error The error that is to be displayed.
         */
        private void showErrorDialogFragment(Throwable error) {
            HelperFragment fragment = HelperFragment.CACHE.getIfPresent(fragmentTag);
            if (fragment == null || fragment.isDetached()) {
                HelperFragment.CACHE.invalidate(fragmentTag);
                return;
            }

            Context context = fragment.getActivity();
            FragmentManager fm = fragment.getFragmentManager();
            handle(context, fm, error);
        }

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
                        try {
                            showErrorDialogFragment(thr);

                        } catch (Throwable err) {
                            // there was an error handling the error. oops.
                            Crashlytics.logException(err);
                        }
                    } finally {
                        removeHelperFragment();
                    }
                }

                @Override
                public void onError(Throwable err) {
                    try {
                        showErrorDialogFragment(err);

                    } catch (Throwable thr) {
                        // there was an error handling the error. oops.
                        Crashlytics.logException(err);
                    }

                    try {
                        subscriber.onCompleted();
                    } catch (Throwable thr) {
                        Crashlytics.logException(err);
                    } finally {
                        removeHelperFragment();
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

    private static Map<Throwable, Instant> PREVIOUS_ERRORS_QUEUE = new HashMap<>();

    public static void handle(Context context, Throwable error) {
        handle(context, null, error);
    }

    public static void handle(Context context, FragmentManager fragmentManager, Throwable error) {
        Log.e("Error", "An error occurred", error);

        // we cant do much formatting without a context
        if (context == null)
            return;

        // do some checking so we don't log this exception twice in one second
        boolean sendToCrashlytics = Settings.of(context).crashlyticsEnabled();
        if (sendToCrashlytics) {
            Instant previousTime = PREVIOUS_ERRORS_QUEUE.get(error);
            if (previousTime != null && Instant.now().minus(1000L).isBefore(previousTime))
                sendToCrashlytics = false;

            PREVIOUS_ERRORS_QUEUE.put(error, Instant.now());
        }

        ErrorFormatting.Formatter<?> formatter = ErrorFormatting.getFormatter(error);
        if (sendToCrashlytics && formatter.shouldSendToCrashlytics())
            Crashlytics.logException(error);

        if (fragmentManager != null) {
            String message = formatter.getMessage(context, error);
            showErrorString(fragmentManager, message);
        }
    }

    public static void showErrorString(FragmentManager fragmentManager, String message) {
        Log.i("Error", message);

        Bundle arguments = new Bundle();
        arguments.putString("content", message);

        ErrorDialogFragment dialog = new ErrorDialogFragment();
        dialog.setArguments(arguments);
        dialog.show(fragmentManager, (String) null);
    }

    public static <T> Observable.Operator<T, T> errorDialog(Fragment fragment) {
        return new ErrorDialogOperator<>(fragment.getChildFragmentManager());
    }

    public static <T> Observable.Operator<T, T> errorDialog(FragmentActivity activity) {
        return new ErrorDialogOperator<>(activity.getSupportFragmentManager());
    }
}
