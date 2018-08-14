package com.pr0gramm.app.util

import android.support.v4.util.CircularArray
import android.util.Log
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import com.pr0gramm.app.BuildConfig
import pl.brightinventions.slf4android.MessageValueSupplier
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord

/**
 */
class LogHandler : Handler() {
    private val messageValueSupplier = MessageValueSupplier()

    private var cachedCrashlytics: CrashlyticsCore? = null
        get() {
            if (field == null) {
                field = Crashlytics.getInstance().core
            }

            return field
        }

    override fun publish(record: java.util.logging.LogRecord) {
        val logThis = BuildConfig.DEBUG || record.level.intValue() >= Level.INFO.intValue()
        if (!logThis)
            return

        val logRecord = pl.brightinventions.slf4android.LogRecord.fromRecord(record)

        // format message if needed
        val needsFormatting = logRecord.parameters.let { it != null && it.isNotEmpty() }
        val formatted = if (needsFormatting) {
            StringBuilder()
                    .apply { messageValueSupplier.append(logRecord, this) }
                    .toString()
        } else {
            logRecord.message
        }

        val tag = record.loggerName
        val androidLogLevel = logRecord.logLevel.androidLevel

        if (!BuildConfig.DEBUG) {
            cachedCrashlytics?.log(androidLogLevel, tag, formatted)
        } else {
            Log.println(androidLogLevel, tag, formatted)
        }

        appendToInternalLogBuffer(record, tag, formatted)
    }

    override fun close() {}

    override fun flush() {}

    private data class LogEntry(val time: Long, val level: Level, val tag: String, val message: String)

    companion object {
        private const val MESSAGE_LIMIT = 4096

        private val BUFFER = CircularArray<LogEntry>(MESSAGE_LIMIT)

        /**
         * Returns a list of the recent messages
         */
        fun recentMessages(): List<String> {
            val entries = synchronized(BUFFER) {
                (0 until BUFFER.size()).map { BUFFER.get(it) }
            }

            // get the most recent entry. should be the last one
            val mostRecentEntry = entries.maxBy { it.time } ?: return emptyList()

            // format each entry relative to the newest one
            return entries.sortedBy { it.time }.map { (time, level, tag, message) ->
                "%4.2fs [%5s] %s: %s".format(0.001f * (time - mostRecentEntry.time), level.name, tag, message)
            }
        }

        private fun appendToInternalLogBuffer(record: LogRecord, tag: String, formatted: String) {
            synchronized(BUFFER) {
                // remove the oldest message if we've reached the limit
                if (BUFFER.size() == MESSAGE_LIMIT) {
                    BUFFER.popLast()
                }

                // and add the new message to the buffer
                BUFFER.addFirst(LogEntry(record.millis, record.level, tag, formatted))
            }
        }
    }
}
