package com.pr0gramm.app.ui.fragments

import com.pr0gramm.app.util.BackgroundScheduler
import com.pr0gramm.app.util.MainThreadScheduler
import rx.Observable
import rx.Subscription
import rx.functions.Action0
import rx.functions.Action1

/**
 */
abstract class LoaderHelper<T> {
    private var cachedValue: T? = null
    private var cachedError: Throwable? = null

    private var valueCallback: Action1<T>? = null
    private var errorCallback: Action1<Throwable>? = null

    private var subscription: Subscription? = null

    protected abstract fun newObservable(): Observable<T>

    fun load(valueCallback: Action1<T>, errorCallback: Action1<Throwable>) {
        register(valueCallback, errorCallback, true)
    }

    fun load(valueCallback: Action1<T>, errorCallback: Action1<Throwable>,
             completeCallback: Action0) {

        register(doFinally(valueCallback, completeCallback),
                doFinally(errorCallback, completeCallback),
                true)
    }

    fun register(valueCallback: Action1<T>, errorCallback: Action1<Throwable>) {
        register(valueCallback, errorCallback, false)
    }

    fun register(valueCallback: Action1<T>, errorCallback: Action1<Throwable>,
                 completeCallback: Action0) {

        register(doFinally(valueCallback, completeCallback),
                doFinally(errorCallback, completeCallback),
                false)
    }

    private fun register(valueCallback: Action1<T>, errorCallback: Action1<Throwable>,
                         startLoading: Boolean) {

        // store for later
        this.valueCallback = valueCallback
        this.errorCallback = errorCallback

        if (cachedValue != null) {
            valueCallback.call(cachedValue)

        } else if (cachedError != null) {
            errorCallback.call(cachedError)

        } else if (subscription == null && startLoading) {
            reload()
        }
    }

    fun reload() {
        if (subscription != null) {
            subscription!!.unsubscribe()
        }

        subscription = newObservable()
                .take(1)
                .subscribeOn(BackgroundScheduler)
                .unsubscribeOn(BackgroundScheduler)
                .observeOn(MainThreadScheduler)
                .subscribe({ value -> this@LoaderHelper.onNext(value) }, { error -> this@LoaderHelper.onError(error) })
    }

    fun detach() {
        valueCallback = null
        errorCallback = null
    }

    private fun onNext(value: T) {
        subscription = null

        cachedValue = value
        cachedError = null

        if (valueCallback != null) {
            valueCallback!!.call(value)
        }
    }

    private fun onError(error: Throwable) {
        subscription = null

        cachedError = error
        cachedValue = null

        if (errorCallback != null) {
            errorCallback!!.call(error)
        }
    }

    companion object {

        fun <T> of(supplier: () -> Observable<T>): LoaderHelper<T> {
            return object : LoaderHelper<T>() {
                override fun newObservable(): Observable<T> {
                    return supplier()
                }
            }
        }

        private fun <T> doFinally(firstAction: Action1<T>, finallyAction: Action0): Action1<T> {
            return Action1 { value ->
                try {
                    firstAction.call(value)
                } finally {
                    finallyAction.call()
                }
            }
        }
    }
}
