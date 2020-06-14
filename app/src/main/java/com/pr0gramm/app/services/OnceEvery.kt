package com.pr0gramm.app.services

import com.pr0gramm.app.Duration

class OnceEvery(val interval: Duration) {
    var last = System.currentTimeMillis()

    fun isTime(): Boolean {
        val now = System.currentTimeMillis()
        if (now - last > interval.millis) {
            last = now
            return true
        }

        return false
    }

    inline operator fun invoke(fn: () -> Unit) {
        if (isTime()) {
            fn()
        }
    }
}