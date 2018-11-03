package com.pr0gramm.app.ui.base

import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import com.pr0gramm.app.ui.dialogs.OnComplete
import com.pr0gramm.app.ui.dialogs.OnNext
import com.pr0gramm.app.ui.dialogs.subscribeWithErrorHandling
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.kodein
import com.pr0gramm.app.util.logger
import com.pr0gramm.app.util.time
import com.trello.rxlifecycle.LifecycleTransformer
import com.trello.rxlifecycle.android.ActivityEvent
import com.trello.rxlifecycle.components.support.RxAppCompatActivity
import kotlinx.coroutines.Job
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.KodeinTrigger
import rx.Observable
import rx.Subscription

/**
 * A [android.support.v7.app.AppCompatActivity] with dagger injection and stuff.
 */
abstract class BaseAppCompatActivity(name: String) : RxAppCompatActivity(), KodeinAware, AndroidCoroutineScope {
    @JvmField
    protected val logger = logger(name)

    override val kodein: Kodein by lazy { (this as Context).kodein }
    override val kodeinTrigger = KodeinTrigger()

    override lateinit var job: Job
    override val androidContext: Context = this

    fun <T> bindUntilEventAsync(event: ActivityEvent): Observable.Transformer<T, T> {
        return AsyncLifecycleTransformer(bindUntilEvent<T>(event))
    }

    fun <T> bindToLifecycleAsync(): LifecycleTransformer<T> {
        return AsyncLifecycleTransformer(bindToLifecycle<T>())
    }

    fun <T> Observable<T>.bindToLifecycle(): Observable<T> = compose(this@BaseAppCompatActivity.bindToLifecycle())

    fun <T> Observable<T>.bindToLifecycleAsync(): Observable<T> = compose(this@BaseAppCompatActivity.bindToLifecycleAsync())

    override fun onCreate(savedInstanceState: Bundle?) {
        job = Job()
        logger.time("Injecting services") { kodeinTrigger.trigger() }
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
