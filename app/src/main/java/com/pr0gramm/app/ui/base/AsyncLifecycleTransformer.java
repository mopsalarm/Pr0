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
    public <U> Single.Transformer<U, U> forSingle() {
        return single -> single
                .subscribeOn(BackgroundScheduler.instance())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(transformer.forSingle());
    }

    @NonNull
    @Override
    public Completable.Transformer forCompletable() {
        return completable -> completable
                .subscribeOn(BackgroundScheduler.instance())
                .unsubscribeOn(BackgroundScheduler.instance())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(transformer.forCompletable());
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
