package com.pr0gramm.app

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.SettableFuture
import com.pr0gramm.app.util.AndroidUtility.doInBackground
import com.timgroup.statsd.NoOpStatsDClient
import com.timgroup.statsd.NonBlockingStatsDClient
import com.timgroup.statsd.StatsDClient
import org.slf4j.LoggerFactory

/**
 * Statsd client helper
 */
object Stats {
    private val logger = LoggerFactory.getLogger("Stats")

    private val CLIENT = SettableFuture.create<StatsDClient>()
    private val EMPTY_CLIENT = NoOpStatsDClient()

    @JvmStatic
    fun get(): StatsDClient {
        if (CLIENT.isDone) {
            return Futures.getUnchecked(CLIENT)
        } else {
            return EMPTY_CLIENT
        }
    }

    fun init(version: Int) {
        doInBackground {
            try {
                logger.info("Create a new statsd client")
                CLIENT.set(NonBlockingStatsDClient("app", "pr0-metrics.wibbly-wobbly.de",
                        8125, 64, "version:" + version, "host:app"))

            } catch (err: Exception) {
                logger.warn("Could not create statsd client, falling back on noop", err)
                CLIENT.set(NoOpStatsDClient())
            }
        }
    }
}
