package com.pr0gramm.app.ui.base

import com.pr0gramm.app.util.BackgroundScheduler
import com.pr0gramm.app.util.observeOnMain
import com.pr0gramm.app.util.subscribeOnBackground
import com.trello.rxlifecycle.LifecycleTransformer

import rx.Completable
import rx.Observable
import rx.Single
import rx.android.schedulers.AndroidSchedulers

/**
 */
class AsyncLifecycleTransformer<T>(
        private val transformer: LifecycleTransformer<T>) : LifecycleTransformer<T> {

    override fun <U> forSingle(): Single.Transformer<U, U> {
        return Single.Transformer<U, U> {
            it.subscribeOn(BackgroundScheduler.instance())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(transformer.forSingle())
        }
    }

    override fun forCompletable(): Completable.Transformer {
        return Completable.Transformer {
            it.subscribeOn(BackgroundScheduler.instance())
                    .unsubscribeOn(BackgroundScheduler.instance())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(transformer.forCompletable())
        }
    }

    override fun call(observable: Observable<T>): Observable<T> {
        return observable
                .subscribeOnBackground()
                .unsubscribeOn(BackgroundScheduler.instance())
                .observeOnMain()
                .compose(transformer)
    }
}
