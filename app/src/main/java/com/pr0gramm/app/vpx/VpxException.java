package com.pr0gramm.app.vpx;

/**
 * A exception that will be raised on vpx errors.
 */
public class VpxException extends RuntimeException {
    public VpxException() {
    }

    public VpxException(String detailMessage) {
        super(detailMessage);
    }
}
