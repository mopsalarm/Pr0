package com.pr0gramm.app.util;

import android.os.Looper;

import rx.Scheduler;
import rx.schedulers.Schedulers;

/**
 * A scheduler that put work out of the main thread. Do nothing, if
 * already on some other thread.
 */
public class BackgroundScheduler extends Scheduler {
    private final Scheduler immidiateScheduler = Schedulers.immediate();
    private final Scheduler backgroundScheduler = Schedulers.io();

    private BackgroundScheduler() {
    }

    @Override
    public Worker createWorker() {
        if (isInBackground()) {
            // we are already in background
            return immidiateScheduler.createWorker();
        } else {
            // put into background
            return backgroundScheduler.createWorker();
        }
    }

    private boolean isInBackground() {
        return Looper.getMainLooper().getThread() != Thread.currentThread();
    }

    private static final BackgroundScheduler INSTANCE = new BackgroundScheduler();

    public static Scheduler instance() {
        return INSTANCE;
    }
}
