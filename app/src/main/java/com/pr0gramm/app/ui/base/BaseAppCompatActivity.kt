package com.pr0gramm.app.ui.base

import android.os.Bundle
import android.view.MotionEvent
import com.github.salomonbrys.kodein.KodeinInjector
import com.github.salomonbrys.kodein.android.AppCompatActivityInjector
import com.pr0gramm.app.ui.dialogs.OnComplete
import com.pr0gramm.app.ui.dialogs.OnNext
import com.pr0gramm.app.ui.dialogs.subscribeWithErrorHandling
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.time
import com.trello.rxlifecycle.LifecycleTransformer
import com.trello.rxlifecycle.android.ActivityEvent
import com.trello.rxlifecycle.components.support.RxAppCompatActivity
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription

/**
 * A [android.support.v7.app.AppCompatActivity] with dagger injection and stuff.
 */
abstract class BaseAppCompatActivity(name: String) : RxAppCompatActivity(), AppCompatActivityInjector {
    @JvmField
    protected val logger: Logger = LoggerFactory.getLogger(name)

    final override val injector = KodeinInjector()
    final override val kodeinComponent = super.kodeinComponent
    final override val kodeinScope = super.kodeinScope

    fun <T> bindUntilEventAsync(event: ActivityEvent): Observable.Transformer<T, T> {
        return AsyncLifecycleTransformer(bindUntilEvent<T>(event))
    }

    fun <T> bindToLifecycleAsync(): LifecycleTransformer<T> {
        return AsyncLifecycleTransformer(bindToLifecycle<T>())
    }

    fun <T> Observable<T>.bindToLifecycle(): Observable<T> = compose(this@BaseAppCompatActivity.bindToLifecycle())

    fun <T> Observable<T>.bindToLifecycleAsync(): Observable<T> = compose(this@BaseAppCompatActivity.bindToLifecycleAsync())

    override fun onCreate(savedInstanceState: Bundle?) {
        logger.time("Injecting services") { initializeInjector() }
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyInjector()
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

    final override fun initializeInjector() = super.initializeInjector()
    final override fun destroyInjector() = super.destroyInjector()


    fun <T> Observable<T>.subscribeWithErrorHandling(
            onComplete: OnComplete = {}, onNext: OnNext<T> = {}): Subscription {

        return subscribeWithErrorHandling(supportFragmentManager, onComplete, onNext)
    }
}
