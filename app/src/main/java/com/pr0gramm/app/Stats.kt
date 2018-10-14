package com.pr0gramm.app

import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.logger
import com.timgroup.statsd.NoOpStatsDClient
import com.timgroup.statsd.NonBlockingStatsDClient
import com.timgroup.statsd.StatsDClient

/**
 * Statsd client helper
 */
object Stats {
    private val logger = logger("Stats")

    @Volatile
    private var CLIENT: StatsDClient? = null

    private val EMPTY_CLIENT = NoOpStatsDClient()

    @JvmStatic
    fun get(): StatsDClient {
        return CLIENT ?: EMPTY_CLIENT
    }

    fun init(version: Int) {
        doInBackground {
            CLIENT = try {
                logger.info("Create a new statsd client")
                NonBlockingStatsDClient("app", "pr0-metrics.wibbly-wobbly.de",
                        8125, 64, "version:$version", "host:app")

            } catch (err: Exception) {
                logger.warn("Could not create statsd client, falling back on noop", err)
                EMPTY_CLIENT
            }
        }
    }
}
