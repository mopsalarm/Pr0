package com.pr0gramm.app;

/**
 */
public class LoginRequiredException extends IllegalStateException {
    public LoginRequiredException() {
    }

    public LoginRequiredException(String detailMessage) {
        super(detailMessage);
    }
}
