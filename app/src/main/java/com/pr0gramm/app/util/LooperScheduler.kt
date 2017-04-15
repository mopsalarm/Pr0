package com.pr0gramm.app.util

import android.os.Handler
import android.os.Looper
import android.os.Message
import rx.Scheduler
import rx.Subscription
import rx.android.plugins.RxAndroidPlugins
import rx.exceptions.OnErrorNotImplementedException
import rx.functions.Action0
import rx.plugins.RxJavaPlugins
import rx.subscriptions.Subscriptions
import java.util.concurrent.TimeUnit

class LooperScheduler private constructor(looper: Looper) : Scheduler() {
    private val handler: Handler = Handler(looper)

    override fun createWorker(): Scheduler.Worker {
        return HandlerWorker(handler)
    }

    internal class HandlerWorker(private val handler: Handler) : Scheduler.Worker() {
        private val hook = RxAndroidPlugins.getInstance().schedulersHook

        @Volatile
        private var unsubscribed: Boolean = false

        override fun unsubscribe() {
            unsubscribed = true
            handler.removeCallbacksAndMessages(this /* token */)
        }

        override fun isUnsubscribed(): Boolean {
            return unsubscribed
        }

        override fun schedule(action: Action0): Subscription {
            return schedule(action, 0, TimeUnit.MILLISECONDS)
        }

        override fun schedule(action: Action0, delayTime: Long, unit: TimeUnit): Subscription {
            var action = action
            if (unsubscribed) {
                return Subscriptions.unsubscribed()
            }

            action = hook.onSchedule(action)

            // short cut and run now.
            if (delayTime == 0L && isTargetThread) {
                action.call()
                return Subscriptions.unsubscribed()
            }

            val scheduledAction = ScheduledAction(action, handler)

            val message = Message.obtain(handler, scheduledAction)
            message.obj = this // Used as token for unsubscription operation.

            handler.sendMessageDelayed(message, unit.toMillis(delayTime))

            if (unsubscribed) {
                handler.removeCallbacks(scheduledAction)
                return Subscriptions.unsubscribed()
            }

            return scheduledAction
        }

        private val isTargetThread: Boolean
            get() = handler.looper.thread === Thread.currentThread()

    }

    internal class ScheduledAction(private val action: Action0, private val handler: Handler) : Runnable, Subscription {
        @Volatile private var unsubscribed: Boolean = false

        override fun run() {
            try {
                action.call()
            } catch (e: Throwable) {
                // nothing to do but print a System error as this is fatal and there is nowhere else to throw this
                val ie = when (e) {
                    is OnErrorNotImplementedException ->
                        IllegalStateException("Exception thrown on Scheduler.Worker thread. Add `onError` handling.", e)

                    else ->
                        IllegalStateException("Fatal Exception thrown on Scheduler.Worker thread.", e)
                }

                RxJavaPlugins.getInstance().errorHandler.handleError(ie)

                val thread = Thread.currentThread()
                thread.uncaughtExceptionHandler.uncaughtException(thread, ie)
            }

        }

        override fun unsubscribe() {
            unsubscribed = true
            handler.removeCallbacks(this)
        }

        override fun isUnsubscribed(): Boolean {
            return unsubscribed
        }
    }

    companion object {
        val MAIN: Scheduler = LooperScheduler(Looper.getMainLooper())
    }
}
