package com.pr0gramm.app.util.scripting;

/**
 * If an error interpreting stuff occurred.
 */
public class InterpreterException extends RuntimeException {
    public InterpreterException(String detailMessage) {
        super(detailMessage);
    }
}
