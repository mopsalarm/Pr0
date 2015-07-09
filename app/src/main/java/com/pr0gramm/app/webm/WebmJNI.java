package com.pr0gramm.app.webm;

import com.google.common.base.Stopwatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class WebmJNI {
    private static final Logger logger = LoggerFactory.getLogger(WebmJNI.class);

    public static native String getVpxString();

    public static native long newVpxWrapper();
    public static native void freeVpxWrapper(long vpx);
    public static native void vpxPutData(long vpx, byte[] data, int offset, int length);
    public static native boolean vpxGetFrame(long vpx);

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

        } catch (Throwable error) {
            logger.info("Could not load library", error);
        }
    }
}
