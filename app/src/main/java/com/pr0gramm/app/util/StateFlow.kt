package com.pr0gramm.app.util

import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.AbstractFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll

private object NoValue

class StateFlow<T> : AbstractFlow<T> {
    private val channel = BroadcastChannel<T>(capacity = 1)
    private var latestValue: Any?

    constructor() {
        latestValue = NoValue
    }

    constructor(value: T) {
        latestValue = value
    }

    val value: T
        get() {
            val value = latestValue
            if (value === NoValue) {
                throw IllegalStateException("no value in StateFlow")
            }

            return value as T
        }

    val valueOrNull: T?
        get() {
            val value = latestValue
            return value.takeUnless { value === NoValue } as T?
        }

    suspend fun send(newValue: T) {
        latestValue = newValue
        channel.send(newValue)
    }

    fun sendOrBlock(newValue: T) {
        latestValue = newValue
        channel.sendBlocking(newValue)
    }

    override suspend fun collectSafely(collector: FlowCollector<T>) {
        latestValue?.let {
            if (it !== NoValue) {
                collector.emit(it as T)
            }
        }
        collector.emitAll(channel.openSubscription())
    }
}