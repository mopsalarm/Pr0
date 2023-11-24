package com.pr0gramm.app.util

import android.content.Context
import com.pr0gramm.app.Logger
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import java.util.Date

class ExceptionHandler private constructor(
        private val delegate: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {

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

        writeStacktraceToFile(thread, err)

        delegate?.uncaughtException(thread, err)
    }

    private fun writeStacktraceToFile(thread: Thread, err: Throwable) {
        runCatching {
            val file = file ?: return

            file.printWriter().use { writer ->
                writer.println("Stack trace at " + Date())
                writer.println("Thread: ${thread.name}")
                writer.println()

                err.printStackTrace(writer)
            }
        }
    }

    companion object {
        private val logger = Logger("ExceptionHandler")

        private var file: File? = null

        fun install(context: Context) {
            logger.info { "Install uncaught exception handler" }

            file = File(context.filesDir, "last-stacktrace.txt")

            val previous = Thread.getDefaultUncaughtExceptionHandler()
            val handler = ExceptionHandler(previous)
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }

        fun previousStackTrace(): String? {
            return runCatching { file?.readText() }.getOrNull()
        }
    }
}