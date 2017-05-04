package com.pr0gramm.app.ui.dialogs

import android.support.v4.app.FragmentManager
import com.pr0gramm.app.util.AndroidUtility
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

fun <T> Observable<T>.ignoreError(): Observable<T> {
    return onErrorResumeNext { err ->
        AndroidUtility.logToCrashlytics(RuntimeException("Ignoring error", err))
        Observable.empty()
    }
}
