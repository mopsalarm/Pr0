package com.pr0gramm.app.util

import com.google.common.base.Optional


inline fun <T> Optional<T>.ifAbsent(fn: () -> Unit): Unit {
    if (!this.isPresent) {
        fn()
    }
}

inline fun <T> Optional<T>.ifPresent(fn: () -> Unit): Unit {
    if (this.isPresent) {
        fn()
    }
}
