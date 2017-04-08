package com.pr0gramm.app.util

import com.google.common.base.Supplier

/**
 */
abstract class Lazy<T> {
    private var value: T? = null

    fun get(): T {
        var result = value
        if (result == null) {
            synchronized(this) {
                result = value
                if (result == null) {
                    result = compute()
                    value = result
                }
            }
        }

        return result!!
    }

    protected abstract fun compute(): T

    companion object {
        /**
         * Creates a new lazy from a supplier.
         */
        @JvmStatic
        fun <T> of(supplier: Supplier<T>): Lazy<T> {
            return object : Lazy<T>() {
                override fun compute(): T {
                    return supplier.get()
                }
            }
        }
    }
}
