package com.pr0gramm.app.ui.fragments;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;

/**
 */
public abstract class LoaderHelper<T> {
    private Optional<T> cachedValue;
    private Throwable cachedError;

    private Action1<T> valueCallback;
    private Action1<Throwable> errorCallback;

    private Subscription subscription;

    abstract protected Observable<T> newObservable();

    public void load(Action1<T> valueCallback, Action1<Throwable> errorCallback) {
        register(valueCallback, errorCallback, true);
    }

    public void load(Action1<T> valueCallback, Action1<Throwable> errorCallback,
                     Action0 completeCallback) {

        register(doFinally(valueCallback, completeCallback),
                doFinally(errorCallback, completeCallback),
                true);
    }

    public void register(Action1<T> valueCallback, Action1<Throwable> errorCallback) {
        register(valueCallback, errorCallback, false);
    }

    public void register(Action1<T> valueCallback, Action1<Throwable> errorCallback,
                         Action0 completeCallback) {

        register(doFinally(valueCallback, completeCallback),
                doFinally(errorCallback, completeCallback),
                false);
    }

    private void register(Action1<T> valueCallback, Action1<Throwable> errorCallback,
                          boolean startLoading) {

        // store for later
        this.valueCallback = valueCallback;
        this.errorCallback = errorCallback;

        if (cachedValue != null) {
            valueCallback.call(cachedValue.orNull());

        } else if (cachedError != null) {
            errorCallback.call(cachedError);

        } else if (subscription == null && startLoading) {
            reload();
        }
    }

    public void reload() {
        if (subscription != null) {
            subscription.unsubscribe();
        }

        subscription = newObservable()
                .first()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onNext, this::onError);
    }

    public void detach() {
        valueCallback = null;
        errorCallback = null;
    }

    private void onNext(T value) {
        subscription = null;

        cachedValue = Optional.fromNullable(value);
        cachedError = null;

        if (valueCallback != null) {
            valueCallback.call(value);
        }
    }

    private void onError(Throwable error) {
        subscription = null;

        cachedError = error;
        cachedValue = null;

        if (errorCallback != null) {
            errorCallback.call(error);
        }
    }

    public static <T> LoaderHelper<T> of(Supplier<Observable<T>> supplier) {
        return new LoaderHelper<T>() {
            @Override
            protected Observable<T> newObservable() {
                return supplier.get();
            }
        };
    }

    public static <T> LoaderHelper<T> of(Observable<T> staticObservable) {
        Observable<T> cached = staticObservable.cache();
        return of(() -> cached);
    }

    private static <T> Action1<T> doFinally(Action1<T> firstAction, Action0 finallyAction) {
        return value -> {
            try {
                firstAction.call(value);
            } finally {
                finallyAction.call();
            }
        };
    }
}
