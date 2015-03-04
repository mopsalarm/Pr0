package com.pr0gramm.app;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;

import com.afollestad.materialdialogs.MaterialDialog;
import com.crashlytics.android.Crashlytics;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import org.joda.time.Instant;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit.RetrofitError;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func2;

import static com.google.common.primitives.Ints.asList;

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

    @SuppressLint("ValidFragment")
    public static class ErrorDialogOperator<T> extends Fragment implements Observable.Operator<T, T> {
        private ErrorDialogOperator(FragmentManager parentFragmentManager) {
            setRetainInstance(true);

            parentFragmentManager.beginTransaction()
                    .add(this, null)
                    .commitAllowingStateLoss();
        }

        private void removeThisFragment() {
            getFragmentManager().beginTransaction()
                    .remove(this)
                    .commitAllowingStateLoss();
        }

        @Override
        public Subscriber<? super T> call(Subscriber<? super T> subscriber) {
            return new Subscriber<T>() {
                @Override
                public void onCompleted() {
                    try {
                        subscriber.onCompleted();

                    } catch (Throwable thr) {
                        try {
                            handle(getActivity(), getFragmentManager(), thr);

                        } catch (Throwable err) {
                            // there was an error handling the error. oops.
                            Crashlytics.logException(err);
                        }
                    } finally {
                        removeThisFragment();
                    }
                }

                @Override
                public void onError(Throwable err) {
                    try {
                        handle(getActivity(), getActivity().getSupportFragmentManager(), err);

                    } catch (Throwable thr) {
                        // there was an error handling the error. oops.
                        Crashlytics.logException(err);
                    }

                    try {
                        subscriber.onCompleted();
                    } catch (Throwable thr) {
                        Crashlytics.logException(err);
                    } finally {
                        removeThisFragment();
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

    public static void handle(Context context, Throwable error) {
        handle(context, null, error);
    }

    private static void handle(Context context, FragmentManager fragmentManager, Throwable error) {
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

        for (Formatter<?> formatter : FORMATTERS) {
            if (formatter.handles(error)) {
                String message = formatter.getMessage(context, error);
                if (sendToCrashlytics && formatter.shouldSendToCrashlytics())
                    Crashlytics.logException(error);

                if (fragmentManager != null)
                    showErrorString(fragmentManager, message);

                break;
            }
        }
    }

    private static Map<Throwable, Instant> PREVIOUS_ERRORS_QUEUE = new HashMap<>();

    private static class Formatter<T extends Throwable> {
        private final Class<T> errorType;
        private final Predicate<T> check;
        private final Func2<T, Context, String> message;
        private boolean log = true;

        public Formatter(Class<T> errorType, Func2<T, Context, String> message) {
            this(errorType, Predicates.alwaysTrue(), message);
        }

        public Formatter(Class<T> errorType, @StringRes int message) {
            this(errorType, Predicates.alwaysTrue(), (err, ctx) -> ctx.getString(message));
        }

        public Formatter(Class<T> errorType, Predicate<T> check, @StringRes int message) {
            this(errorType, check, (err, ctx) -> ctx.getString(message));
        }

        public Formatter(Class<T> errorType, Predicate<T> check, Func2<T, Context, String> message) {
            this.errorType = errorType;
            this.check = check;
            this.message = message;
        }

        /**
         * Tests if this formatter handles the given exception.
         */
        public boolean handles(Throwable thr) {
            //noinspection unchecked
            return errorType.isInstance(thr) && check.apply((T) thr);
        }

        /**
         * Gets the message for the given exception. You must only call this,
         * if {@link #handles(Throwable)} returned true before.
         */
        public String getMessage(Context context, Throwable thr) {
            //noinspection unchecked
            return message.call((T) thr, context);
        }

        /**
         * Deactivates logging of this kind of error.
         *
         * @return this instance.
         */
        public Formatter<T> quiet() {
            log = false;
            return this;
        }

        /**
         * Returns true, if this exception should be logged
         */
        public boolean shouldSendToCrashlytics() {
            return log;
        }
    }

    private static class RetrofitStatusFormatter extends Formatter<RetrofitError> {
        public RetrofitStatusFormatter(Predicate<RetrofitError> check, @StringRes int message) {
            super(RetrofitError.class,
                    err -> err.getResponse() != null && check.apply(err),
                    message);
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

    public static <T> ErrorDialogOperator<T> errorDialog(Fragment fragment) {
        return new ErrorDialogOperator<>(fragment.getChildFragmentManager());
    }

    public static <T> ErrorDialogOperator<T> errorDialog(FragmentActivity activity) {
        return new ErrorDialogOperator<>(activity.getSupportFragmentManager());
    }

    /**
     * Returns a list containing multiple error formatters in the order they should
     * be applied.
     *
     * @return The error formatters.
     */
    private static List<Formatter<?>> makeErrorFormatters() {
        final List<Formatter<?>> formatters = new ArrayList<>();

        Func2<Throwable, Context, String> guessMessage = (err, context) -> {
            String message = err.getLocalizedMessage();
            if (Strings.isNullOrEmpty(message))
                message = err.getMessage();

            if (Strings.isNullOrEmpty(message))
                message = context.getString(R.string.error_exception_of_type, err.getClass().getSimpleName());

            return message;
        };

        formatters.add(new RetrofitStatusFormatter(
                err -> asList(401, 403).contains(err.getResponse().getStatus()),
                R.string.error_not_authorized).quiet());

        formatters.add(new RetrofitStatusFormatter(
                err -> err.getResponse().getStatus() == 404,
                R.string.error_not_found).quiet());

        formatters.add(new RetrofitStatusFormatter(
                err -> err.getResponse().getStatus() == 503,
                R.string.error_service_unavailable).quiet());

        // could not deserialize. this one i am interested in.
        formatters.add(new Formatter<>(RetrofitError.class,
                err -> err.getKind() == RetrofitError.Kind.CONVERSION,
                R.string.error_conversion));

        formatters.add(new Formatter<>(RetrofitError.class,
                err -> err.getCause() instanceof UnknownHostException,
                R.string.error_host_not_found).quiet());

        formatters.add(new Formatter<>(RetrofitError.class,
                err -> err.getCause() instanceof ConnectException,
                (err, context) -> {
                    String host = Uri.parse(err.getUrl()).getHost();
                    return context.getString(R.string.error_connect_exception, host);
                }).quiet());

        // add a default formatter for io exceptions, but do not log them
        formatters.add(new Formatter<>(IOException.class, guessMessage::call).quiet());

        // oops
        formatters.add(new Formatter<>(NullPointerException.class, R.string.error_nullpointer));

        // no memory, this is bad!
        formatters.add(new Formatter<>(OutOfMemoryError.class, R.string.error_oom));

        // add a default formatter.
        formatters.add(new Formatter<>(Throwable.class, guessMessage::call));

        return formatters;
    }

    private static final List<Formatter<?>> FORMATTERS = ImmutableList.copyOf(makeErrorFormatters());
}
