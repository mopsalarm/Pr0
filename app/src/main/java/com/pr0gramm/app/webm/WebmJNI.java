package com.pr0gramm.app.webm;

import com.google.common.base.Stopwatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class WebmJNI {
    private static final Logger logger = LoggerFactory.getLogger(WebmJNI.class);

    public static native String getVpxString();

    public static native String getWebmString();

    private static boolean hasNativeLibrary;

    public static void loadNativeLibrary() {
        if (hasNativeLibrary)
            return;

        try {
            logger.info("Loading library now");

            Stopwatch watch = Stopwatch.createStarted();
            System.loadLibrary("pr0-webm-jni");
            hasNativeLibrary = true;

            logger.info("Native library loaded in {}", watch);
            logger.info("  vpx version: {}", getVpxString());
            logger.info("  webm version: {}", getWebmString());

        } catch (Throwable error) {
            logger.info("Could not load library", error);
        }
    }
}
