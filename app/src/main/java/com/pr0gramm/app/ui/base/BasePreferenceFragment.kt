package com.pr0gramm.app.ui.base

import android.content.Context
import androidx.annotation.CallSuper
import androidx.preference.PreferenceFragmentCompat
import com.pr0gramm.app.Logger
import com.pr0gramm.app.time
import com.pr0gramm.app.util.di.LazyInjectorAware
import com.pr0gramm.app.util.di.PropertyInjector

abstract class BasePreferenceFragment(name: String) : PreferenceFragmentCompat(), LazyInjectorAware {

    protected val logger = Logger(name)

    override val injector: PropertyInjector = PropertyInjector()

    @CallSuper
    override fun onAttach(context: Context) {
        logger.time("Injecting services") {
            injector.inject(context)
        }

        super.onAttach(context)
    }
}