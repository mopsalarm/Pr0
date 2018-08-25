package com.pr0gramm.app.util

class StateTransaction<V : Any>(private val valueSupplier: () -> V, private val applyChange: () -> Unit) {
    private val logger = logger("StateTransaction")
    private var txLevel = 0

    operator fun <T> invoke(dispatch: Dispatch = Dispatch.CHANGED, tx: () -> T): T {
        AndroidUtility.checkMainThread()

        val previousState = valueSupplier()

        txLevel++
        val result = try {
            tx()
        } finally {
            txLevel--
        }

        if (dispatch === Dispatch.NEVER) {
            logger.debug("Not doing state update, cause dispatch=NEVER")
        } else {
            if (dispatch === Dispatch.ALWAYS || (!isActive && previousState != valueSupplier())) {
                logger.debug("Running deferred state update in transaction")
                applyChange()
            }
        }

        return result
    }

    val isActive: Boolean get() = txLevel > 0

    enum class Dispatch {
        ALWAYS, CHANGED, NEVER
    }
}
