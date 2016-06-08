package com.pr0gramm.app;

import android.support.annotation.NonNull;

import com.timgroup.statsd.NoOpStatsDClient;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Statsd client helper
 */
public class Stats {
    private static final Logger logger = LoggerFactory.getLogger("Stats");

    private static StatsDClient CLIENT;

    @NonNull
    public static StatsDClient get() {
        if (CLIENT == null) {
            synchronized (Stats.class) {
                if (CLIENT == null) {
                    try {
                        logger.info("Create a new statsd client");
                        CLIENT = new NonBlockingStatsDClient("app", "pr0-metrics.wibbly-wobbly.de", 8125, 64);

                    } catch (Exception err) {
                        logger.warn("Could not create statsd client, falling back on noop", err);
                        CLIENT = new NoOpStatsDClient();
                    }
                }
            }
        }

        return CLIENT;
    }
}
