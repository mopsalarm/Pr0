package com.pr0gramm.app.ui.base;

import android.support.annotation.NonNull;

import com.pr0gramm.app.util.BackgroundScheduler;
import com.trello.rxlifecycle.LifecycleTransformer;

import rx.Completable;
import rx.Observable;
import rx.Single;
import rx.android.schedulers.AndroidSchedulers;

/**
 */
class AsyncLifecycleTransformer<T> implements LifecycleTransformer<T> {
    private final LifecycleTransformer<T> transformer;

    AsyncLifecycleTransformer(LifecycleTransformer<T> transformer) {
        this.transformer = transformer;
    }

    @NonNull
    @Override
    public Single.Transformer<T, T> forSingle() {
        return single -> single.toObservable().compose(this).toSingle();
    }

    @NonNull
    @Override
    public Completable.CompletableTransformer forCompletable() {
        return completable -> completable.<T>toObservable().compose(this).toCompletable();
    }

    @Override
    public Observable<T> call(Observable<T> observable) {
        return observable
                .subscribeOn(BackgroundScheduler.instance())
                .unsubscribeOn(BackgroundScheduler.instance())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(transformer);
    }
}
