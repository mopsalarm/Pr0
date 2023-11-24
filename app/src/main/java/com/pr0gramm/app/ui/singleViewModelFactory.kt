package com.pr0gramm.app.ui

import android.os.Bundle
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.createViewModelLazy
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.pr0gramm.app.util.di.Injector
import com.pr0gramm.app.util.di.injector
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun <VM : ViewModel> singleViewModelFactory(fragment: Fragment, expectedModelClass: Class<VM>, create: Injector.(SavedStateHandle) -> VM): ViewModelProvider.Factory {
    return object : AbstractSavedStateViewModelFactory(fragment, Bundle.EMPTY) {
        override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
            require(modelClass.isAssignableFrom(expectedModelClass)) {
                "Cannot create instance of $modelClass, this factory only creates $expectedModelClass"
            }

            val injector = fragment.requireContext().injector
            val model = injector.create(handle)
            return modelClass.cast(model) as T
        }
    }
}

@MainThread
inline fun <reified VM : ViewModel> Fragment.viewModels(
        noinline ownerProducer: () -> ViewModelStoreOwner = { this },
        noinline create: Injector.(SavedStateHandle) -> VM): Lazy<VM> {

    return createViewModelLazy(VM::class, { ownerProducer().viewModelStore }) {
        singleViewModelFactory(this, VM::class.java, create)
    }
}


open class SavedStateAccessor(private val handle: SavedStateHandle) {
    protected fun <T : Any> savedStateValue(key: String): ReadWriteProperty<SavedStateAccessor, T?> {
        return object : ReadWriteProperty<SavedStateAccessor, T?> {
            override fun getValue(thisRef: SavedStateAccessor, property: KProperty<*>): T? {
                return handle.get<T>(key)
            }

            override fun setValue(thisRef: SavedStateAccessor, property: KProperty<*>, value: T?) {
                handle.set(key, value)
            }
        }
    }

    protected fun <T : Any> savedStateValue(key: String, defaultValue: T): ReadWriteProperty<SavedStateAccessor, T> {
        return object : ReadWriteProperty<SavedStateAccessor, T> {
            override fun getValue(thisRef: SavedStateAccessor, property: KProperty<*>): T {
                return handle.get<T>(key) ?: defaultValue
            }

            override fun setValue(thisRef: SavedStateAccessor, property: KProperty<*>, value: T) {
                handle.set(key, value)
            }
        }
    }
}
