package com.pr0gramm.app.ui.views

import android.content.Context
import com.pr0gramm.app.util.di.injector

interface InjectorViewMixin {
    fun getContext(): Context
}

inline fun <reified T : Any> InjectorViewMixin.instance(): Lazy<T> = lazy(LazyThreadSafetyMode.PUBLICATION) {
    getContext().injector.instance<T>()
}