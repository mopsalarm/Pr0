package com.pr0gramm.app.ui.dialogs

import android.support.v4.app.FragmentManager
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscriber
import rx.Subscription

typealias OnNext<T> = (T) -> Unit
typealias OnError = (Throwable) -> Unit
typealias OnComplete = () -> Unit


fun <T> Observable<T>.subscribeWithErrorHandling(
        fm: FragmentManager, onComplete: OnComplete = {}, onNext: OnNext<T> = {}): Subscription {

    return subscribe(object : Subscriber<T>() {
        override fun onNext(value: T) {
            onNext(value)
        }

        override fun onCompleted() {
            onComplete()
        }

        override fun onError(error: Throwable) {
            ErrorDialogFragment.defaultOnError().call(error)
        }
    })
}

fun <T> Observable<T>.ignoreError(msg: String = "Ignoring error"): Observable<T> {
    return onErrorResumeNext { err ->
        LoggerFactory.getLogger("Error").warn(msg, err)
        Observable.empty()
    }
}