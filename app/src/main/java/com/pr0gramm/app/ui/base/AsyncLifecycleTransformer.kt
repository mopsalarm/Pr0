package com.pr0gramm.app.ui.base

import com.pr0gramm.app.util.BackgroundScheduler
import com.pr0gramm.app.util.MainThreadScheduler
import com.pr0gramm.app.util.observeOnMainThread
import com.pr0gramm.app.util.subscribeOnBackground
import com.trello.rxlifecycle.LifecycleTransformer
import rx.Completable
import rx.Observable
import rx.Single

/**
 */
class AsyncLifecycleTransformer<T>(
        private val transformer: LifecycleTransformer<T>) : LifecycleTransformer<T> {

    override fun <U> forSingle(): Single.Transformer<U, U> {
        return Single.Transformer<U, U> {
            it.subscribeOn(BackgroundScheduler)
                    .observeOn(MainThreadScheduler)
                    .compose(transformer.forSingle())
        }
    }

    override fun forCompletable(): Completable.Transformer {
        return Completable.Transformer {
            it.subscribeOn(BackgroundScheduler)
                    .unsubscribeOn(BackgroundScheduler)
                    .observeOn(MainThreadScheduler)
                    .compose(transformer.forCompletable())
        }
    }

    override fun call(observable: Observable<T>): Observable<T> {
        return observable
                .subscribeOnBackground()
                .unsubscribeOn(BackgroundScheduler)
                .observeOnMainThread()
                .compose(transformer)
    }
}
