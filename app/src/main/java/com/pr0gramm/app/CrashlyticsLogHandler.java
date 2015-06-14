package com.pr0gramm.app;

import com.crashlytics.android.Crashlytics;

import java.util.logging.Handler;

import pl.brightinventions.slf4android.LogRecord;
import pl.brightinventions.slf4android.MessageValueSupplier;

/**
 */
class CrashlyticsLogHandler extends Handler {
    private final MessageValueSupplier messageValueSupplier = new MessageValueSupplier();

    @Override
    public void publish(java.util.logging.LogRecord record) {
        LogRecord logRecord = pl.brightinventions.slf4android.LogRecord.fromRecord(record);
        StringBuilder messageBuilder = new StringBuilder();
        messageValueSupplier.append(logRecord, messageBuilder);

        String tag = record.getLoggerName();
        int androidLogLevel = logRecord.getLogLevel().getAndroidLevel();
        Crashlytics.getInstance().core.log(androidLogLevel, tag, messageBuilder.toString());
    }

    @Override
    public void close() {
    }

    @Override
    public void flush() {
    }
}
