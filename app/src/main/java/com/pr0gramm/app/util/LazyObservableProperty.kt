package com.pr0gramm.app.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class LazyObservableProperty<T>(
        private val initialValueSupplier: () -> T,
        private val onValueUpdated: (old: T, new: T) -> Unit) : ReadWriteProperty<Any, T> {

    private var value: T? = null

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        if (value == null) {
            value = initialValueSupplier()
        }

        return value!!
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        val previousValue = this.getValue(thisRef, property)

        this.value = value

        if (previousValue != value) {
            onValueUpdated(previousValue, value)
        }
    }
}
