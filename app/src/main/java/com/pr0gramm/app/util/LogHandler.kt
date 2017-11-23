package com.pr0gramm.app.util

import android.support.v4.util.CircularArray
import android.util.Log
import com.crashlytics.android.Crashlytics
import com.pr0gramm.app.BuildConfig
import pl.brightinventions.slf4android.MessageValueSupplier
import java.util.logging.Handler
import java.util.logging.Level

/**
 */
class LogHandler : Handler() {
    private val messageValueSupplier = MessageValueSupplier()

    private val crashlytics by lazy(LazyThreadSafetyMode.NONE) {
        Crashlytics.getInstance().core
    }

    override fun publish(record: java.util.logging.LogRecord) {
        val logThis = BuildConfig.DEBUG || record.level.intValue() >= Level.INFO.intValue()
        if (!logThis)
            return

        val messageBuilder = StringBuilder()
        val logRecord = pl.brightinventions.slf4android.LogRecord.fromRecord(record)
        messageValueSupplier.append(logRecord, messageBuilder)

        val formatted = messageBuilder.toString()

        val tag = record.loggerName
        val androidLogLevel = logRecord.logLevel.androidLevel

        if (BuildConfig.DEBUG) {
            Log.println(androidLogLevel, tag, formatted)
        } else {
            crashlytics.log(androidLogLevel, tag, formatted)
        }

        synchronized(BUFFER) {
            // remove the oldest message if we've reached the limit
            if (BUFFER.size() == MESSAGE_LIMIT) {
                BUFFER.popLast()
            }

            // and add the new message to the buffer
            BUFFER.addFirst(LogEntry(record.millis, record.level, tag, formatted))
        }
    }

    override fun close() {}

    override fun flush() {}

    private data class LogEntry(val time: Long, val level: Level, val tag: String, val message: String)

    companion object {
        const val MESSAGE_LIMIT = 4096

        private val BUFFER = CircularArray<LogEntry>(MESSAGE_LIMIT)

        /**
         * Returns a list of the recent messages
         */
        fun recentMessages(): List<String> {
            val entries = synchronized(BUFFER) {
                (0..BUFFER.size() - 1).map { BUFFER.get(it) }
            }

            // get the most recent entry. should be the last one
            val mostRecentEntry = entries.maxBy { it.time } ?: return emptyList()

            // format each entry relative to the newest one
            return entries.sortedBy { it.time }.map { (time, level, tag, message) ->
                "%4.2fs [%5s] %s: %s".format(0.001f * (time - mostRecentEntry.time), level.name, tag, message)
            }
        }
    }
}
