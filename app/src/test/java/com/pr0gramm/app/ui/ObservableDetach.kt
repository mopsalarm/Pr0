package com.pr0gramm.app.ui

import com.pr0gramm.app.util.debug
import com.pr0gramm.app.util.decoupleSubscribe
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import pl.brightinventions.slf4android.LoggerConfiguration
import rx.Observable
import rx.lang.kotlin.subscribeBy
import rx.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import java.util.logging.ConsoleHandler

class ObservableDetach {
    val logger = LoggerFactory.getLogger("ObservableDetach")

    @Before
    fun initializeLogging() {
        LoggerConfiguration.configuration()
                .removeRootLogcatHandler()
                .addHandlerToRootLogger(ConsoleHandler())
    }

    @Test
    fun autoConnect() {
        val stopper = Observable.just(true).delay(1, TimeUnit.SECONDS)

        networkCall
                .debug("network")
                .decoupleSubscribe()
                .observeOn(Schedulers.computation())
                .takeUntil(stopper)
                .debug("caller")
                .subscribeBy(
                        onNext = {
                            logger.info("Network call gave: {}")
                        },
                        onError = { err ->
                            logger.info("Error was: $err")
                        })

        logger.info("waiting for test to finish.")
        Thread.sleep(5000)
    }

    val networkCall = Observable.fromCallable {
        try {
            logger.info("Starting network call.")
            Thread.sleep(4000)
            logger.info("Network call finished.")
        } catch(err: InterruptedException) {
            logger.warn("Network call was interrupted")
            throw err
        }

        true
    }
}
