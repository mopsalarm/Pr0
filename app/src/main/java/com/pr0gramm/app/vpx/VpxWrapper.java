package com.pr0gramm.app.vpx;

import android.graphics.Bitmap;

import com.google.common.base.Stopwatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;

import static com.google.common.base.Preconditions.checkState;

/**
 */
class VpxWrapper implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(VpxWrapper.class);
    private final long vpx;
    private boolean closed;

    private VpxWrapper(long vpx) {
        this.vpx = vpx;
    }

    public void put(byte[] data, int offset, int length) {
        checkValid();
        vpxPutData(vpx, data, offset, length);
    }

    public boolean get(Bitmap bitmap) {
        checkValid();
        return vpxGetFrame(vpx);
    }

    private void checkValid() {
        checkState(!closed, "vpx wrapper is already closed");
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            vpxFreeWrapper(vpx);
        }
    }

    public static VpxWrapper newInstance() {
        checkState(hasNativeLibrary, "Native library not loaded");
        return new VpxWrapper(vpxNewWrapper());
    }

    public static boolean isAvailable() {
        return hasNativeLibrary;
    }

    private static native String getVpxString();

    private static native long vpxNewWrapper();

    private static native void vpxFreeWrapper(long vpx);

    private static native void vpxPutData(long vpx, byte[] data, int offset, int length);

    private static native boolean vpxGetFrame(long vpx);

    private static boolean hasNativeLibrary;

    private static void loadNativeLibrary() {
        try {
            logger.info("Loading library now");

            Stopwatch watch = Stopwatch.createStarted();
            System.loadLibrary("vpx-wrapper");
            hasNativeLibrary = true;

            logger.info("Native library loaded in {}", watch);
            logger.info("  vpx version: {}", getVpxString());

        } catch (Throwable error) {
            logger.warn("Could not load library", error);
        }
    }

    static {
        loadNativeLibrary();
    }
}
