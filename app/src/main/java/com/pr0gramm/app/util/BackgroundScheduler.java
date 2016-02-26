package com.pr0gramm.app.util;

import rx.Scheduler;
import rx.schedulers.Schedulers;

/**
 * A scheduler that put work on some background scheduler
 */
public class BackgroundScheduler {
    public static Scheduler instance() {
        return Schedulers.io();
    }
}
