package com.pr0gramm.app.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.TimeUnit;

import rx.Scheduler;
import rx.Subscription;
import rx.android.plugins.RxAndroidPlugins;
import rx.functions.Action0;
import rx.internal.schedulers.ScheduledAction;
import rx.subscriptions.CompositeSubscription;
import rx.subscriptions.Subscriptions;

/**
 */
public class HandlerThreadScheduler extends Scheduler {
    private final Handler handler;

    /**
     */
    private HandlerThreadScheduler(Handler handler) {
        this.handler = handler;
    }

    @Override
    public Worker createWorker() {
        return new InnerHandlerThreadScheduler(handler);
    }

    private static class InnerHandlerThreadScheduler extends Worker {

        private final Handler handler;

        private final CompositeSubscription compositeSubscription = new CompositeSubscription();

        public InnerHandlerThreadScheduler(Handler handler) {
            this.handler = handler;
        }

        @Override
        public void unsubscribe() {
            compositeSubscription.unsubscribe();
        }

        @Override
        public boolean isUnsubscribed() {
            return compositeSubscription.isUnsubscribed();
        }

        @Override
        public Subscription schedule(Action0 action, long delayTime, TimeUnit unit) {
            action = RxAndroidPlugins.getInstance().getSchedulersHook().onSchedule(action);

            // short cut and run now.
            if(delayTime == 0 && isTargetThread()) {
                action.call();
                return Subscriptions.unsubscribed();
            }

            final ScheduledAction scheduledAction = new ScheduledAction(action);
            scheduledAction.add(Subscriptions.create(() -> handler.removeCallbacks(scheduledAction)));
            scheduledAction.addParent(compositeSubscription);
            compositeSubscription.add(scheduledAction);

            handler.postDelayed(scheduledAction, unit.toMillis(delayTime));

            return scheduledAction;
        }

        private boolean isTargetThread() {
            return handler.getLooper().getThread() == Thread.currentThread();
        }

        @Override
        public Subscription schedule(final Action0 action) {
            return schedule(action, 0, TimeUnit.MILLISECONDS);
        }
    }

    public static final Scheduler INSTANCE =
            new HandlerThreadScheduler(new Handler(Looper.getMainLooper()));
}
