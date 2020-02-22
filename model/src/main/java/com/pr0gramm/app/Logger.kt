package com.pr0gramm.app

import android.os.SystemClock
import android.util.Log
import com.pr0gramm.app.model.BuildConfig
import java.util.concurrent.atomic.AtomicInteger

inline class Logger(val name: String) {
    inline fun debug(block: () -> String) {
        if (BuildConfig.DEBUG) {
            Logging.log(Log.DEBUG, name, block())
        }
    }

    inline fun info(block: () -> String) {
        Logging.log(Log.INFO, name, block())
    }

    inline fun warn(block: () -> String) {
        Logging.log(Log.WARN, name, block())
    }

    inline fun warn(err: Throwable, block: () -> String) {
        warn(block(), err)
    }

    inline fun error(block: () -> String) {
        Logging.log(Log.ERROR, name, block())
    }

    inline fun error(err: Throwable, block: () -> String) {
        error(block(), err)
    }

    fun warn(text: String, err: Throwable) {
        warn { text + "\n" + Log.getStackTraceString(err) }
    }

    fun error(text: String, err: Throwable) {
        error { text + "\n" + Log.getStackTraceString(err) }
    }
}

typealias RemoteLoggingHandler = (level: Int, tag: String, message: String) -> Boolean

object Logging {
    private const val ENTRY_BUFFER_SIZE = 4096

    class Entry(
            var time: Long = 0,
            var level: Int = 0,
            var name: String = "",
            var message: String = "",
            var thread: String = "")

    private val levels = arrayOf("0", "1", "VERBOSE", "DEBUG", "INFO", "WARN", "ERROR", "ASSERT")

    private val entries = Array(ENTRY_BUFFER_SIZE) { Entry() }
    private val entriesIndex = AtomicInteger()

    private var remoteLoggingHandler: RemoteLoggingHandler = { _, _, _ -> false }

    fun configureLoggingOutput(handler: RemoteLoggingHandler) {
        for (idx in 0..entriesIndex.get()) {
            val entry = entries.getOrNull(idx) ?: break
            handler(entry.level, "[${entry.thread}] ${entry.name}", entry.message)
        }

        remoteLoggingHandler = handler

        log(Log.INFO, "Logging", "Remote logging configured, previous messages replayed")
    }

    fun log(level: Int, name: String, message: String) {
        val thread = Thread.currentThread().name
        val tag = "[$thread] $name"

        val logged = name != "OkHttpClient" && remoteLoggingHandler(level, tag, message)
        if (!logged) {
            if (BuildConfig.DEBUG) {
                // log to stdout
                Log.println(level, tag, message)
            }
        }

        // store in buffer. Not 100% thread safe but close enough.
        val index = entriesIndex.getAndIncrement() % ENTRY_BUFFER_SIZE
        entries[index].apply {
            this.time = SystemClock.elapsedRealtime()
            this.level = level
            this.name = name
            this.message = message
            this.thread = thread
        }
    }

    fun recentMessages(): List<String> {
        var result: List<String> = listOf()

        repeat(16) { _ ->
            val now = SystemClock.elapsedRealtime()

            val beginIndex = entriesIndex.get()

            result = entries.indices.reversed().mapNotNullTo(ArrayList(entries.size)) { offset ->
                val entry = entries[(beginIndex + offset) % entries.size]
                entry.takeIf { it.time != 0L }?.let {
                    "%4.3fs [%16s] [%5s] %s: %s".format(0.001f * (entry.time - now),
                            entry.thread, levelToString(entry.level), entry.name, entry.message)
                }
            }

            // logs did not change, take them
            if (beginIndex == entriesIndex.get()) {
                return result
            }
        }

        return result
    }

    private fun levelToString(level: Int): String {
        return levels.getOrNull(level) ?: "DEBUG"
    }
}

inline fun <T> Logger.time(name: String, supplier: () -> T): T {
    if (BuildConfig.DEBUG) {
        val watch = Stopwatch()
        var success = true

        return try {
            try {
                supplier()
            } catch (err: Exception) {
                success = false
                warn { "$name failed after $watch" }
                throw err
            }
        } finally {
            if (success) {
                debug { "$name took $watch" }
            }
        }

    } else {
        return supplier()
    }
}

fun Logger.warnWithStack(skipStack: Int, message: () -> String) {
    if (BuildConfig.DEBUG) {
        val stack = Thread.currentThread().stackTrace
                .drop(skipStack + 4)
                .filter { it.className.startsWith("com.pr0gramm.app.") }

        warn {
            val stackStr = stack.joinToString("\n") { "  $it" }
            "${message()} at\n$stackStr"
        }
    }
}
