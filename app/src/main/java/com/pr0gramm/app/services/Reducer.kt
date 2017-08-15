package com.pr0gramm.app.services

import com.pr0gramm.app.util.createObservable
import rx.Emitter
import rx.Observable
import java.util.concurrent.ExecutionException


object Reducer {
    class Step<out K, out T>(val value: T, val next: K?)

    fun <K, T> iterate(seed: K, block: (K) -> Step<K, T>): Observable<T> {

        return createObservable(Emitter.BackpressureMode.BUFFER) { emitter ->
            var nextKey: K? = seed

            try {
                while (nextKey != null) {
                    val step = block(nextKey)
                    emitter.onNext(step.value)

                    nextKey = step.next
                }

                emitter.onCompleted()

            } catch(err: ExecutionException) {
                emitter.onError(err.cause ?: err)
            } catch(err: Throwable) {
                emitter.onError(err)
            }
        }
//
//
//        fun iterate(key: K): Observable<T> {
//            return block(key).flatMapObservable { step ->
//                var oValue = Observable.just(step.value)
//
//                if (step.next != null) {
//                    // iterate if needed. We need to use subscribeOn here to avoid a real recursion
//                    // by starting the next call in a another thread.
//                    oValue = oValue.concatWith(iterate(step.next)
//                            .subscribeOn(BackgroundScheduler.instance())
//                            .observeOn(BackgroundScheduler.instance()))
//                }
//
//                oValue
//            }
//        }
//
//        return iterate(seed)
    }
}
