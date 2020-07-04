package com.pr0gramm.app.ui

import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.createViewModelLazy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.pr0gramm.app.util.di.Injector
import com.pr0gramm.app.util.di.injector

inline fun <reified T : ViewModel> Fragment.singleViewModelFactory(noinline create: Injector.() -> T): ViewModelProvider.Factory {
    val expectedModelClass = T::class.java

    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(expectedModelClass)) {
                "Cannot create instance of $modelClass, this factory only creates $expectedModelClass"
            }

            val injector = requireContext().injector
            val model = injector.create()
            return modelClass.cast(model) as T
        }
    }
}

@MainThread
inline fun <reified VM : ViewModel> Fragment.viewModels(
        noinline ownerProducer: () -> ViewModelStoreOwner = { this },
        noinline create: Injector.() -> VM): Lazy<VM> {

    return createViewModelLazy(VM::class, { ownerProducer().viewModelStore }) {
        singleViewModelFactory(create)
    }
}
