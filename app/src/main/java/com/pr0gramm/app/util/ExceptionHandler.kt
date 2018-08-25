package com.pr0gramm.app.util

class ExceptionHandler private constructor(val delegate: Thread.UncaughtExceptionHandler) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, err: Throwable) {
        if (err is IllegalStateException) {
            // this is an exception happening in gms ads. we'll ignore it.
            if (err.message == "Results have already been set") {
                if (err.stackTrace.any { it.methodName == "setResult" }) {
                    return
                }
            }

        }

        delegate.uncaughtException(thread, err)
    }

    companion object {
        private val logger = logger("ExceptionHandler")

        fun install() {
            logger.info("Install uncaught exception handler")
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            val handler = ExceptionHandler(previous)
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }
}