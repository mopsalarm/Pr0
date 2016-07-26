package com.pr0gramm.app.util;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import rx.Observable;
import rx.Single;
import rx.subjects.BehaviorSubject;

/**
 * Holds an {@link rx.Single} value and provides access
 * to the result.
 */
public class Holder<T> {
    private final BehaviorSubject<T> subject = BehaviorSubject.create();
    private SettableFuture<T> future = SettableFuture.create();

    private Holder(Single<T> single) {
        single.subscribe(
                value -> {
                    future.set(value);
                    subject.onNext(value);
                },
                error -> {
                    future.setException(error);
                    subject.onError(error);
                });
    }

    public Single<T> asSingle() {
        return subject.take(1).toSingle();
    }

    public Observable<T> asObservable() {
        return subject.take(1);
    }

    public Optional<T> asOptional() {
        return future.isDone() ? Optional.of(value()) : Optional.absent();
    }

    public T value() {
        return Futures.getUnchecked(future);
    }

    public static <T> Holder<T> ofSingle(Single<T> single) {
        return new Holder<>(single);
    }

    public static <T> Holder<T> ofObservable(Observable<T> observable) {
        return ofSingle(observable.toSingle());
    }
}
