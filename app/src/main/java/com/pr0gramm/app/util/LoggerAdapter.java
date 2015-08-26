package com.pr0gramm.app.util;

import org.slf4j.Logger;

import retrofit.RestAdapter;

/**
 */
public class LoggerAdapter implements RestAdapter.Log {
    private final Logger logger;

    public LoggerAdapter(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void log(String message) {
        logger.info("{}", message);
    }
}
