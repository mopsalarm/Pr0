package com.pr0gramm.app.services

import rx.Observable
import rx.Single


object Reducer {
    class Step<out K, out T>(val value: T, val next: K?)

    sealed class Notification<out T, out K> {
        class Result<out T, out K>(val value: T) : Notification<T, K>()
        class Update<out T, out K>(val key: K, val value: T) : Notification<T, K>()
    }

    fun <K, E, T> reduce(seedStep: K, startValue: T, combine: (T, E) -> T,
                         block: (K) -> Single<Step<K, E>>): Observable<Notification<T, K>> {

        fun iterate(state: T, key: K): Observable<Notification<T, K>> {
            return block(key).flatMapObservable { nextStep ->
                val updatedState = combine(state, nextStep.value)

                val next = nextStep.next
                if (next != null) {
                    return@flatMapObservable Observable
                            .just(Notification.Update(key, updatedState))
                            .concatMap { iterate(updatedState, next) }

                } else {
                    return@flatMapObservable Observable.just(Notification.Result<T, K>(updatedState))
                }
            }
        }

        return iterate(startValue, seedStep)
    }

    fun <K, E> reduceToList(seedStep: K, block: (K) -> Single<Step<K, List<E>>>): Observable<Notification<List<E>, K>> {
        fun combine(result: MutableList<E>, values: List<E>): MutableList<E> {
            result.addAll(values)
            return result
        }

        // do the mapping and combining!
        val result = reduce<K, List<E>, MutableList<E>>(seedStep, mutableListOf<E>(), ::combine, block)
        return result.map { it as Notification<List<E>, K> }
    }

    fun <T, K> unpack(o: Observable<Notification<T, K>>): Observable<T> {
        return o.flatMap { n ->
            when (n) {
                is Notification.Result<T, K> -> Observable.just(n.value)
                else -> Observable.empty()
            }
        }
    }
}
