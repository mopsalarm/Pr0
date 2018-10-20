package com.pr0gramm.app.util

import android.os.SystemClock
import android.util.Log
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import com.pr0gramm.app.BuildConfig
import java.util.concurrent.atomic.AtomicInteger


fun logger(name: String): KLogger = KLogger(name)

object Logging {
    private data class Entry(var time: Long, var level: Int, var name: String, var message: String)

    private val entries = Array(4096) { Entry(0, 0, "", "") }
    private val entriesIndex = AtomicInteger()

    private var cachedCrashlytics: CrashlyticsCore? = null
        get() {
            if (field == null) {
                Crashlytics.getInstance()?.let {
                    field = it.core
                }
            }

            return field
        }

    fun log(level: Int, name: String, text: String) {
        val message = "[${Thread.currentThread().name}] $text"

        if (BuildConfig.DEBUG) {
            // only log to logcat.
            Log.println(level, name, message)

        } else {
            cachedCrashlytics?.log(level, name, message)
        }

        // store in buffer. Not 100% thread safe but close enough.
        val index = entriesIndex.getAndIncrement() % entries.size
        entries[index].apply {
            this.time = SystemClock.elapsedRealtime()
            this.level = level
            this.name = name
            this.message = message
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
                    "%4.3fs [%5s] %s: %s".format(0.001f * (entry.time - now),
                            levelToString(entry.level), entry.name, entry.message)
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
        return when (level) {
            Log.DEBUG -> "DEBUG"
            Log.INFO -> "INFO"
            Log.WARN -> "WARN"
            Log.ERROR -> "ERROR"
            else -> "LOG"
        }
    }
}

class KLogger(val name: String) {
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

    inline fun error(block: () -> String) {
        Logging.log(Log.ERROR, name, block())
    }

    fun warn(text: String, err: Throwable) {
        warn { text + "\n" + Log.getStackTraceString(err) }
    }

    fun error(text: String, err: Throwable) {
        error { text + "\n" + Log.getStackTraceString(err) }
    }
}
