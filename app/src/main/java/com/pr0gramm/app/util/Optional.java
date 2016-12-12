package com.pr0gramm.app.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A better optional
 */
@ParametersAreNonnullByDefault
public final class Optional<T> {
    @Nullable
    private final T value;

    private Optional(@Nullable T value) {
        this.value = value;
    }

    public boolean isPresent() {
        return value != null;
    }

    public boolean isAbsent() {
        return value == null;
    }

    /**
     * Calls the method if the value is present.
     */
    public Optional<T> ifPresent(Action1<? super T> action) {
        if (value != null)
            action.call(value);

        return this;
    }

    public Optional<T> ifAbsent(Action0 action) {
        if (value == null)
            action.call();

        return this;
    }

    @NonNull
    public <R> Optional<R> map(Func1<? super T, ? extends R> transformer) {
        if (value == null)
            return absent();

        return fromNullable(transformer.call(value));
    }

    @NonNull
    public <R> Optional<R> flatMap(Func1<? super T, ? extends Optional<R>> transformer) {
        if (value == null)
            return absent();

        Optional<R> result = transformer.call(value);
        return result != null && result.value != null ? result : absent();
    }

    @NonNull
    public Optional<T> filter(Predicate<? super T> predicate) {
        if (value != null && predicate.apply(value))
            return this;

        return absent();
    }

    @NonNull
    public Optional<T> filterNot(Predicate<? super T> predicate) {
        if (value != null && !predicate.apply(value))
            return this;

        return absent();
    }

    @NonNull
    public Optional<T> or(Optional<T> other) {
        return value != null ? this : other;
    }

    @NonNull
    public T get() {
        if (value == null)
            throw new IllegalStateException("Called get() on an empty optional.");

        return value;
    }

    @NonNull
    public T getOr(T fallback) {
        return value != null ? value : fallback;

    }

    @NonNull
    public <X extends Throwable> T getOrThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (value != null)
            return value;

        throw exceptionSupplier.get();
    }

    @Nullable
    public T getOrNull() {
        return value;
    }

    public Observable<T> toObservable() {
        return value != null ? Observable.just(value) : Observable.empty();
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 1234;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Optional && Objects.equal(value, ((Optional) obj).value);
    }

    @Override
    public String toString() {
        if (value != null) {
            return "Optional.of(" + value + ")";
        } else {
            return "Optional.absent()";
        }
    }

    /**
     * Creates a new optional
     */
    @NonNull
    public static <T> Optional<T> fromNullable(@Nullable T value) {
        if (value != null) {
            return new Optional<>(value);
        } else {
            return absent();
        }
    }

    /**
     * Creates a new optional from a non-null value.
     */
    @NonNull
    public static <T> Optional<T> of(@NonNull T value) {
        return new Optional<>(checkNotNull(value));
    }

    @NonNull
    public static <T> Optional<T> of(com.google.common.base.Optional<T> other) {
        return fromNullable(other.orNull());
    }

    /**
     * Gets an empty observable
     */
    @NonNull
    public static <T> Optional<T> absent() {
        //noinspection unchecked
        return ABSENT;
    }

    private static final Optional ABSENT = new Optional<>(null);
}
