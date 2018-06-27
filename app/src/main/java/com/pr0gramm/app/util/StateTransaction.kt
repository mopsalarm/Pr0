package com.pr0gramm.app.util

import org.slf4j.LoggerFactory

class StateTransaction<V : Any>(private val valueSupplier: () -> V, private val applyChange: () -> Unit) {
    private val logger = LoggerFactory.getLogger("StateTransaction")
    private var txLevel = 0

    operator fun <T> invoke(tx: () -> T): T {
        AndroidUtility.checkMainThread()

        val previousState = valueSupplier()

        txLevel++
        val result = try {
            tx()
        } finally {
            txLevel--
        }

        if (!isActive && previousState != valueSupplier()) {
            logger.debug("Running deferred state update in transaction")
            applyChange()
        }

        return result
    }

    val isActive: Boolean get() = txLevel > 0
}
