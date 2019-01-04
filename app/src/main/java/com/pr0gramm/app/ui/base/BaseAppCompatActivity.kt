package com.pr0gramm.app.ui.base

import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import com.pr0gramm.app.ui.dialogs.OnComplete
import com.pr0gramm.app.ui.dialogs.OnNext
import com.pr0gramm.app.ui.dialogs.subscribeWithErrorHandling
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.Logger
import com.pr0gramm.app.util.di.LazyInjectorAware
import com.pr0gramm.app.util.di.PropertyInjector
import com.pr0gramm.app.util.time
import com.trello.rxlifecycle.android.ActivityEvent
import com.trello.rxlifecycle.components.support.RxAppCompatActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import rx.Observable
import rx.Subscription

/**
 * A [android.support.v7.app.AppCompatActivity] with dagger injection and stuff.
 */
abstract class BaseAppCompatActivity(name: String) : RxAppCompatActivity(), LazyInjectorAware, AndroidCoroutineScope {
    protected val logger = Logger(name)

    override val injector: PropertyInjector = PropertyInjector()

    override lateinit var job: Job
    override val androidContext: Context = this

    fun <T> bindUntilEventAsync(event: ActivityEvent): Observable.Transformer<T, T> {
        return AsyncLifecycleTransformer(bindUntilEvent<T>(event))
    }

    fun <T> Observable<T>.bindToLifecycle(): Observable<T> = compose(
            this@BaseAppCompatActivity.bindToLifecycle())

    fun <T> Observable<T>.bindToLifecycleAsync(): Observable<T> = compose(
            AsyncLifecycleTransformer(this@BaseAppCompatActivity.bindToLifecycle()))

    override fun onCreate(savedInstanceState: Bundle?) {
        job = SupervisorJob()
        logger.time("Injecting services") { injector.inject(this) }
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        return try {
            super.dispatchTouchEvent(ev)
        } catch (err: IllegalArgumentException) {
            AndroidUtility.logToCrashlytics(err)
            true
        } catch (err: IllegalArgumentException) {
            AndroidUtility.logToCrashlytics(err)
            true
        }
    }

    fun <T> Observable<T>.subscribeWithErrorHandling(
            onComplete: OnComplete = {}, onNext: OnNext<T> = {}): Subscription {

        return subscribeWithErrorHandling(supportFragmentManager, onComplete, onNext)
    }
}
