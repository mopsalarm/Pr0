package com.pr0gramm.app.ui.base

import android.os.Bundle
import android.view.MotionEvent
import com.pr0gramm.app.Logger
import com.pr0gramm.app.time
import com.pr0gramm.app.util.di.LazyInjectorAware
import com.pr0gramm.app.util.di.PropertyInjector
import rx.Observable

/**
 * A [android.support.v7.app.AppCompatActivity] with dagger injection and stuff.
 */
abstract class BaseAppCompatActivity(name: String) : RxAppCompatActivity(), LazyInjectorAware {
    protected val logger = Logger(name)

    override val injector: PropertyInjector = PropertyInjector()

    fun <T> Observable<T>.bindToLifecycle(): Observable<T> = compose(
            this@BaseAppCompatActivity.bindToLifecycle())

    override fun onCreate(savedInstanceState: Bundle?) {
        logger.time("Injecting services") {
            injector.inject(this)
        }

        super.onCreate(savedInstanceState)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        return try {
            super.dispatchTouchEvent(ev)
        } catch (err: IllegalArgumentException) {
            true
        }
    }
}
