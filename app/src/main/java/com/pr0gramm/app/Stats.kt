package com.pr0gramm.app

import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.logger
import com.timgroup.statsd.NoOpStatsDClient
import com.timgroup.statsd.NonBlockingStatsDClient
import com.timgroup.statsd.StatsDClient
import java.util.concurrent.atomic.AtomicReference

/**
 * Statsd client helper
 */
object Stats {
    private val logger = logger("Stats")

    private val CLIENT = AtomicReference<StatsDClient>()

    private val EMPTY_CLIENT = NoOpStatsDClient()

    @JvmStatic
    fun get(): StatsDClient {
        return CLIENT.get() ?: EMPTY_CLIENT
    }

    fun init(version: Int) {
        doInBackground {
            try {
                logger.info("Create a new statsd client")
                CLIENT.set(NonBlockingStatsDClient("app", "pr0-metrics.wibbly-wobbly.de",
                        8125, 64, "version:$version", "host:app"))

            } catch (err: Exception) {
                logger.warn("Could not create statsd client, falling back on noop", err)
                CLIENT.set(EMPTY_CLIENT)
            }
        }
    }
}
