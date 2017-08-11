package com.pr0gramm.app.services

import rx.Observable
import rx.Single


object Reducer {
    class Step<out K, out T>(val value: T, val next: K?)

    fun <K, T> iterate(seedStep: K, block: (K) -> Single<Step<K, T>>): Observable<T> {

        fun iterate(key: K): Observable<T> {
            return block(key).flatMapObservable { step ->
                val oValue = Observable.just(step.value)
                step.next?.let { next -> oValue.concatWith(iterate(next)) } ?: oValue
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
