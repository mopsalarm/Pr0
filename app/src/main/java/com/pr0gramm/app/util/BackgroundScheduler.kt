package com.pr0gramm.app.util

import rx.Scheduler
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

/**
 * A scheduler that put work on some background scheduler
 */
val BackgroundScheduler: Scheduler = Schedulers.io()

/**
 * A scheduler to run work on the main thread.
 */
val MainThreadScheduler: Scheduler = AndroidSchedulers.mainThread()