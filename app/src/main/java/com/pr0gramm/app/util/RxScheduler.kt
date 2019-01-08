package com.pr0gramm.app.util

import rx.Scheduler
import rx.Subscription
import rx.functions.Action0
import rx.internal.schedulers.NewThreadWorker
import rx.subscriptions.CompositeSubscription
import rx.subscriptions.Subscriptions
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Copied from rx.CachedThreadScheduler, but using a stack instead of a queue for the
 * expiringWorkers
 */
class CachedThreadScheduler(prefix: String) : Scheduler() {
    private val pool: AtomicReference<CachedWorkerPool> = AtomicReference(CachedWorkerPool(NamedThreadFactory(prefix)))

    private class CachedWorkerPool(private val threadFactory: ThreadFactory) {
        private val expiringWorkerStack = LinkedBlockingDeque<ThreadWorker>()

        private val logger = Logger("RxThreads")
        private val usedWorkerCount = AtomicInteger(1)

        init {
            val evictorService = Executors.newScheduledThreadPool(1) { r ->
                val thread = threadFactory.newThread(r)
                thread.name = thread.name + " (Evictor)"
                thread
            }

            NewThreadWorker.tryEnableCancelPolicy(evictorService)

            evictorService.scheduleWithFixedDelay({ evictExpiredWorkers() },
                    KEEP_ALIVE_NANOS, KEEP_ALIVE_NANOS, TimeUnit.NANOSECONDS)

        }

        fun get(): ThreadWorker {
            val count = usedWorkerCount.incrementAndGet()
            logger.debug { "Now using $count threads" }

            while (!expiringWorkerStack.isEmpty()) {
                val threadWorker = expiringWorkerStack.pollLast()
                if (threadWorker != null) {
                    return threadWorker
                }
            }

            // No cached worker found, so create a new one.
            return ThreadWorker(threadFactory)
        }

        fun release(threadWorker: ThreadWorker) {
            usedWorkerCount.decrementAndGet()

            // Refresh expire time before putting worker back in pool
            threadWorker.expirationTime = now() + KEEP_ALIVE_NANOS
            expiringWorkerStack.offerLast(threadWorker)
        }

        fun evictExpiredWorkers() {
            if (!expiringWorkerStack.isEmpty()) {
                val currentTimestamp = now()

                for (threadWorker in expiringWorkerStack) {
                    if (threadWorker.expirationTime <= currentTimestamp) {
                        expiringWorkerStack.remove(threadWorker)
                    } else {
                        // Queue is ordered with the worker that will expire first in the beginning, so when we
                        // find a non-expired worker we can stop evicting.
                        break
                    }
                }
            }
        }

        fun now(): Long {
            return System.nanoTime()
        }
    }

    override fun createWorker(): Scheduler.Worker {
        return EventLoopWorker(pool.get())
    }

    private class EventLoopWorker(private val pool: CachedWorkerPool) : Scheduler.Worker(), Action0 {
        private val once: AtomicBoolean = AtomicBoolean()
        private val innerSubscription = CompositeSubscription()
        private val threadWorker: ThreadWorker = pool.get()

        override fun unsubscribe() {
            if (once.compareAndSet(false, true)) {
                // unsubscribe should be idempotent, so only do this once

                // Release the worker _after_ the previous action (if any) has completed
                threadWorker.schedule(this)
            }
            innerSubscription.unsubscribe()
        }

        override fun call() {
            pool.release(threadWorker)
        }

        override fun isUnsubscribed(): Boolean {
            return innerSubscription.isUnsubscribed
        }

        override fun schedule(action: Action0): Subscription {
            return schedule(action, 0, null)
        }

        override fun schedule(action: Action0, delayTime: Long, unit: TimeUnit?): Subscription {
            if (innerSubscription.isUnsubscribed) {
                // don't schedule, we are unsubscribed
                return Subscriptions.unsubscribed()
            }

            val s = threadWorker.scheduleActual({
                if (!isUnsubscribed) {
                    action.call()
                }
            }, delayTime, unit)

            innerSubscription.add(s)
            s.addParent(innerSubscription)
            return s
        }
    }

    private class ThreadWorker(threadFactory: ThreadFactory) : NewThreadWorker(threadFactory) {
        var expirationTime: Long = 0
    }

    companion object {
        private const val KEEP_ALIVE_NANOS = 5_000_000_000 // 5 seconds
    }

    class NamedThreadFactory(private val prefix: String) : ThreadFactory {
        private val threadIndex = AtomicInteger()
        private val group = ThreadGroup(prefix)

        override fun newThread(r: Runnable): Thread {
            val thread = Thread(group, r)
            thread.name = prefix + "-" + threadIndex.getAndIncrement()
            thread.priority = Thread.MIN_PRIORITY
            thread.threadGroup
            return thread
        }
    }
}
