package com.pr0gramm.app.services

import com.pr0gramm.app.util.BackgroundScheduler
import rx.Observable
import rx.Single


object Reducer {
    class Step<out K, out T>(val value: T, val next: K?)

    fun <K, T> iterate(seedStep: K, block: (K) -> Single<Step<K, T>>): Observable<T> {

        fun iterate(key: K): Observable<T> {
            return block(key).flatMapObservable { step ->
                var oValue = Observable.just(step.value)

                if (step.next != null) {
                    // iterate if needed. We need to use subscribeOn here to avoid a real recursion
                    // by starting the next call in a another thread.
                    val nextIteration = iterate(step.next).subscribeOn(BackgroundScheduler.instance())
                    oValue = oValue.concatWith(nextIteration)
                }

                oValue
            }
        }

        return iterate(seedStep)
    }

    fun <K, E> reduceToList(seedStep: K, block: (K) -> Single<Step<K, List<E>>>): Observable<List<E>> {
        fun combine(result: MutableList<E>, values: List<E>): MutableList<E> {
            result.addAll(values)
            return result
        }

        return iterate(seedStep, block)
                .reduce(mutableListOf<E>(), ::combine)
                .map { it as List<E> }
    }
}
