package com.pr0gramm.app.util

import com.crashlytics.android.Crashlytics
import pl.brightinventions.slf4android.MessageValueSupplier
import java.util.logging.Handler
import java.util.logging.Level

/**
 */
class CrashlyticsLogHandler : Handler() {
    private val messageValueSupplier = MessageValueSupplier()

    override fun publish(record: java.util.logging.LogRecord) {
        if (record.level.intValue() <= Level.INFO.intValue()) {
            val messageBuilder = StringBuilder()
            val logRecord = pl.brightinventions.slf4android.LogRecord.fromRecord(record)
            messageValueSupplier.append(logRecord, messageBuilder)

            val tag = record.loggerName
            val androidLogLevel = logRecord.logLevel.androidLevel
            Crashlytics.getInstance().core.log(androidLogLevel, tag, messageBuilder.toString())
        }
    }

    override fun close() {}

    override fun flush() {}
}
