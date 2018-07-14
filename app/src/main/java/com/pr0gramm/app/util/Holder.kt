package com.pr0gramm.app.util

import rx.Observable
import rx.Single
import rx.subjects.BehaviorSubject

/**
 * Holds an [rx.Single] value and provides access
 * to the result.
 */
class Holder<T> private constructor(single: Single<T>) {
    private val subject = BehaviorSubject.create<T>()

    init {
        single.subscribe(
                { value ->
                    subject.onNext(value)
                },
                { error ->
                    subject.onError(error)
                })
    }

    fun asObservable(): Observable<T> {
        return subject.take(1)
    }

    /**
     * Gets the value of this holder. This will block, if the value
     * is not yet present.
     */
    val value: T get() = subject.toBlocking().first()

    /**
     * Returns the current value or returns null, if the value is
     * not yet available.
     */
    val valueOrNull: T? get() = subject.value

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
