package com.pr0gramm.app;

import android.os.AsyncTask;
import android.support.annotation.NonNull;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
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

    private static SettableFuture<StatsDClient> CLIENT = SettableFuture.create();

    @NonNull
    public static StatsDClient get() {
        return Futures.getUnchecked(CLIENT);
    }

    public static void init(int version) {
        AsyncTask.execute(() -> {
            try {
                logger.info("Create a new statsd client");
                CLIENT.set(new NonBlockingStatsDClient("app", "pr0-metrics.wibbly-wobbly.de", 8125, 64, "version:" + version));

            } catch (Exception err) {
                logger.warn("Could not create statsd client, falling back on noop", err);
                CLIENT.set(new NoOpStatsDClient());
            }
        });
    }
}
