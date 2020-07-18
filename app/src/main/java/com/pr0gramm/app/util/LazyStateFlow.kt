package com.pr0gramm.app.util

import kotlinx.coroutines.flow.*

interface LazyStateFlow<T : Any?> : Flow<T> {
    val value: T
    val valueOrNull: T?
    fun send(newValue: T)
}

fun <T : Any> lazyStateFlow(initialValue: T): LazyStateFlow<T> {
    val delegate = MutableStateFlow(initialValue)

    return object : LazyStateFlow<T>, Flow<T> by delegate {
        override val value: T
            get() = delegate.value

        override val valueOrNull: T?
            get() = value

        override fun send(newValue: T) {
            delegate.value = newValue
        }
    }
}

inline fun <reified T : Any?> lazyStateFlow(): LazyStateFlow<T> {
    val noValue = Any()
    val delegate: MutableStateFlow<Any?> = MutableStateFlow(noValue)

    return object : AbstractFlow<T>(), LazyStateFlow<T> {
        override val value: T
            get() = delegate.value.let { value ->
                if (value === noValue) {
                    throw IllegalStateException("no value in StateFlow")
                }

                value as T
            }

        override val valueOrNull: T?
            get() {
                val value = delegate.value
                return value.takeUnless { value === noValue } as T?
            }

        override fun send(newValue: T) {
            delegate.value = newValue
        }

        override suspend fun collectSafely(collector: FlowCollector<T>) {
            val values: Flow<T> = delegate.filter { it !== noValue }.filterIsInstance()
            collector.emitAll(values)
        }
    }
}
