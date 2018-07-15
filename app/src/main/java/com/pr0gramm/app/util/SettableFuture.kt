package com.pr0gramm.app.util

import rx.internal.operators.BlockingOperatorToFuture.toFuture
import rx.subjects.BehaviorSubject
import java.util.concurrent.Future

class SettableFuture<V> private constructor(private val subject: BehaviorSubject<V>) :
        Future<V> by toFuture(subject.first()) {

    fun setValue(value: V) {
        subject.onNext(value)
    }

    fun setError(error: Throwable) {
        subject.onError(error)
    }

    companion object {
        operator fun <V> invoke(): SettableFuture<V> = SettableFuture(BehaviorSubject.create())
    }
}
