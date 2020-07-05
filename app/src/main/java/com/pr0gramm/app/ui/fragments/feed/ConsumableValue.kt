package com.pr0gramm.app.ui.fragments.feed

class ConsumableValue<T : Any>(value: T) {
    private var value: T? = value

    fun consume(block: (T) -> Unit) {
        value?.let { currentValue ->
            value = null
            block(currentValue)
        }
    }
}