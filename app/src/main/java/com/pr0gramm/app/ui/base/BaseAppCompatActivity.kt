package com.pr0gramm.app.ui.base

import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import com.pr0gramm.app.util.Logger
import com.pr0gramm.app.util.di.LazyInjectorAware
import com.pr0gramm.app.util.di.PropertyInjector
import com.pr0gramm.app.util.time
import com.trello.rxlifecycle.components.support.RxAppCompatActivity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rx.Observable

/**
 * A [android.support.v7.app.AppCompatActivity] with dagger injection and stuff.
 */
abstract class BaseAppCompatActivity(name: String) : RxAppCompatActivity(), LazyInjectorAware, AndroidCoroutineScope {
    protected val logger = Logger(name)

    override val injector: PropertyInjector = PropertyInjector()

    override lateinit var job: Job
    override val androidContext: Context get() = this

    fun <T> Observable<T>.bindToLifecycle(): Observable<T> = compose(
            this@BaseAppCompatActivity.bindToLifecycle())

    override fun onCreate(savedInstanceState: Bundle?) {
        job = SupervisorJob()

        logger.time("Injecting services") {
            injector.inject(this)
        }

        super.onCreate(savedInstanceState)
    }

    private lateinit var onStartScope: AndroidCoroutineScope
    private lateinit var onResumeScope: AndroidCoroutineScope

    final override fun onStart() {
        super.onStart()

        onStartScope = newChild()
        onStartScope.launch(start = CoroutineStart.UNDISPATCHED) {
            doOnStart()
        }
    }

    protected open suspend fun doOnStart() {}

    override fun onResume() {
        onResumeScope = onStartScope.newChild()
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        onResumeScope.cancelScope()
    }

    override fun onStop() {
        super.onStop()
        onStartScope.cancelScope()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelScope()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        return try {
            super.dispatchTouchEvent(ev)
        } catch (err: IllegalArgumentException) {
            true
        }
    }
}
