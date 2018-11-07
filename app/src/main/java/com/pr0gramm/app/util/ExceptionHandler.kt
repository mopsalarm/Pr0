package com.pr0gramm.app.util

import kotlinx.coroutines.CancellationException
import java.io.IOException

class ExceptionHandler private constructor(private val delegate: Thread.UncaughtExceptionHandler) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, err: Throwable) {
        if (err.causalChain.containsType<CancellationException>()) {
            return
        }

        if (err is IllegalStateException) {
            // this is an exception happening in gms ads. we'll ignore it.
            if (err.message == "Results have already been set") {
                if (err.stackTrace.any { it.methodName == "setResult" }) {
                    return
                }
            }
        }

        if (err is IOException) {
            logger.warn(err) { "Uncaught IOException" }
            return
        }

        delegate.uncaughtException(thread, err)
    }

    companion object {
        private val logger = logger("ExceptionHandler")

        fun install() {
            logger.info { "Install uncaught exception handler" }
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            val handler = ExceptionHandler(previous)
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }
}