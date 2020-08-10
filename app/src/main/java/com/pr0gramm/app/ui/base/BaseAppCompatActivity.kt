package com.pr0gramm.app.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.pr0gramm.app.Logger
import com.pr0gramm.app.time
import com.pr0gramm.app.util.di.LazyInjectorAware
import com.pr0gramm.app.util.di.PropertyInjector

/**
 * A [android.support.v7.app.AppCompatActivity] with dagger injection and stuff.
 */
abstract class BaseAppCompatActivity(name: String) : AppCompatActivity(), LazyInjectorAware {
    protected val logger = Logger(name)

    override val injector: PropertyInjector = PropertyInjector()

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

fun <T : ViewBinding> AppCompatActivity.bindViews(inflate: (layoutInflater: LayoutInflater) -> T): Lazy<T> {
    return lazy(LazyThreadSafetyMode.PUBLICATION) {
        inflate(layoutInflater)
    }
}

