package com.pr0gramm.app;

import android.os.Environment;

import com.google.common.base.Optional;

import java.io.File;

/**
 */
public class LogcatUtility {
    private LogcatUtility() {
    }

    public static Optional<File> dump() {
        try {
            File logFile = new File(Environment.getExternalStorageDirectory(), "pr0gramm.log");
            Process process = Runtime.getRuntime().exec(new String[]{
                    "sh", "-c", "logcat -d > " + logFile.getPath()
            });

            process.waitFor();

            return Optional.of(logFile);

        } catch (Exception ignored) {
            return Optional.absent();
        }
    }
}
