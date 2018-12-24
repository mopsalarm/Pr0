package com.pr0gramm.app.ui.dialogs

import com.pr0gramm.app.util.Logger
import rx.Observable
import rx.Subscriber
import rx.Subscription

typealias OnNext<T> = (T) -> Unit
typealias OnError = (Throwable) -> Unit
typealias OnComplete = () -> Unit


fun <T> Observable<T>.subscribeWithErrorHandling(
        fm: androidx.fragment.app.FragmentManager, onComplete: OnComplete = {}, onNext: OnNext<T> = {}): Subscription {

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
        Logger("Error").warn(msg, err)
        Observable.empty()
    }
}