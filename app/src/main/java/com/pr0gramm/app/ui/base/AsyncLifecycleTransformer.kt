package com.pr0gramm.app.ui.base

import com.pr0gramm.app.util.BackgroundScheduler
import com.pr0gramm.app.util.observeOnMainThread
import com.pr0gramm.app.util.subscribeOnBackground
import com.trello.rxlifecycle.LifecycleTransformer
import rx.Observable

/**
 */
class AsyncLifecycleTransformer<T>(
        private val transformer: LifecycleTransformer<T>) : Observable.Transformer<T, T> {

    override fun call(observable: Observable<T>): Observable<T> {
        return observable
                .subscribeOnBackground()
                .unsubscribeOn(BackgroundScheduler)
                .observeOnMainThread()
                .compose(transformer)
    }
}
