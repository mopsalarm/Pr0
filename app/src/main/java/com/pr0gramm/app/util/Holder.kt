package com.pr0gramm.app.util

import com.google.common.base.Optional
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.SettableFuture

import rx.Observable
import rx.Single
import rx.subjects.BehaviorSubject

/**
 * Holds an [rx.Single] value and provides access
 * to the result.
 */
class Holder<T> private constructor(single: Single<T>) {
    private val subject = BehaviorSubject.create<T>()
    private val future = SettableFuture.create<T>()

    init {
        single.subscribe(
                { value ->
                    future.set(value)
                    subject.onNext(value)
                },
                { error ->
                    future.setException(error)
                    subject.onError(error)
                })
    }

    fun asSingle(): Single<T> {
        return subject.take(1).toSingle()
    }

    fun asObservable(): Observable<T> {
        return subject.take(1)
    }

    fun asOptional(): Optional<T> {
        return if (future.isDone) Optional.of(value()) else Optional.absent<T>()
    }

    fun value(): T {
        return Futures.getUnchecked(future)
    }

    companion object {
        @JvmStatic
        fun <T> ofSingle(single: Single<T>): Holder<T> {
            return Holder(single)
        }

        @JvmStatic
        fun <T> ofObservable(observable: Observable<T>): Holder<T> {
            return ofSingle(observable.toSingle())
        }
    }
}
