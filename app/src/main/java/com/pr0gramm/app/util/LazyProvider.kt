package com.pr0gramm.app.util

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

private object Empty

class Memorizer<in T, out R>(val provider: (T) -> R) : ReadOnlyProperty<Any?, (T) -> R> {
    private var value: Any? = Empty

    @Suppress("UNCHECKED_CAST")
    private val getter: (T) -> R = { t ->
        if (value === Empty) {
            value = provider(t)
        }

        value as R
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): (T) -> R {
        return getter
    }
}

fun <T, R> memorize(provider: (T) -> R) = Memorizer(provider)
