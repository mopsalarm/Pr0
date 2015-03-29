package com.pr0gramm.app;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.StringRes;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import retrofit.RetrofitError;
import rx.functions.Func2;

import static com.google.common.primitives.Ints.asList;

/**
 * This provides utilities for formatting of exceptions..
 */
public class ErrorFormatting {
    public static Formatter<?> getFormatter(Throwable error) {
        for (Formatter<?> formatter : FORMATTERS) {
            if (formatter.handles(error))
                return formatter;
        }

        throw new IllegalStateException("There should always be a default formatter");
    }

    public static class Formatter<T extends Throwable> {
        private final Class<T> errorType;
        private final Predicate<T> check;
        private final Func2<T, Context, String> message;
        private boolean report = true;

        private Formatter(Class<T> errorType, Func2<T, Context, String> message) {
            this(errorType, Predicates.alwaysTrue(), message);
        }

        private Formatter(Class<T> errorType, @StringRes int message) {
            this(errorType, Predicates.alwaysTrue(), (err, ctx) -> ctx.getString(message));
        }

        private Formatter(Class<T> errorType, Predicate<T> check, @StringRes int message) {
            this(errorType, check, (err, ctx) -> ctx.getString(message));
        }

        private Formatter(Class<T> errorType, Predicate<T> check, Func2<T, Context, String> message) {
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
        Formatter<T> doNotReport() {
            report = false;
            return this;
        }

        /**
         * Returns true, if this exception should be logged
         */
        public boolean shouldSendToCrashlytics() {
            return report;
        }
    }

    private static class RetrofitStatusFormatter extends Formatter<RetrofitError> {
        public RetrofitStatusFormatter(Predicate<RetrofitError> check, @StringRes int message) {
            super(RetrofitError.class,
                    err -> err.getResponse() != null && check.apply(err),
                    message);
        }
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
                R.string.error_not_authorized).doNotReport());

        formatters.add(new RetrofitStatusFormatter(
                err -> err.getResponse().getStatus() == 404,
                R.string.error_not_found).doNotReport());

        formatters.add(new RetrofitStatusFormatter(
                err -> err.getResponse().getStatus() / 100 == 5,
                R.string.error_service_unavailable).doNotReport());

        // could not deserialize. this one i am interested in.
        formatters.add(new Formatter<>(RetrofitError.class,
                err -> err.getKind() == RetrofitError.Kind.CONVERSION,
                R.string.error_conversion));

        formatters.add(new Formatter<>(RetrofitError.class,
                err -> err.getCause() instanceof UnknownHostException,
                R.string.error_host_not_found).doNotReport());

        formatters.add(new Formatter<>(RetrofitError.class,
                err -> err.getCause() instanceof TimeoutException,
                R.string.error_timeout).doNotReport());

        formatters.add(new Formatter<>(RetrofitError.class,
                err -> err.getCause() instanceof ConnectException,
                (err, context) -> {
                    String host = Uri.parse(err.getUrl()).getHost();
                    return context.getString(R.string.error_connect_exception, host);
                }).doNotReport());

        formatters.add(new Formatter<>(RetrofitError.class,
                err -> err.getCause() instanceof SocketException,
                R.string.error_socket).doNotReport());

        // add a default formatter for io exceptions, but do not log them
        formatters.add(new Formatter<>(IOException.class, guessMessage::call).doNotReport());

        // oops
        formatters.add(new Formatter<>(NullPointerException.class, R.string.error_nullpointer));

        // no memory, this is bad!
        formatters.add(new Formatter<>(OutOfMemoryError.class, R.string.error_oom));

        // add a default formatter.
        formatters.add(new Formatter<>(Throwable.class, guessMessage::call));

        return formatters;
    }

    public static final ImmutableList<Formatter<?>> FORMATTERS = ImmutableList.copyOf(makeErrorFormatters());
}
