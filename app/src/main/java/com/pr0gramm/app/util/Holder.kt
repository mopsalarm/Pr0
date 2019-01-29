package com.pr0gramm.app.util

import com.pr0gramm.app.ui.base.AsyncScope
import kotlinx.coroutines.async

/**
 * Holds a deferred value and provides access to the result.
 */
class Holder<T>(provider: suspend () -> T) {
    private val deferred = AsyncScope.async { provider() }

    /**
     * Returns the current value or returns null, if the value is
     * not yet available.
     */
    val valueOrNull: T?
        get() {
            if (deferred.isActive || deferred.isCancelled)
                return null

            return runCatching { deferred.getCompleted() }.getOrNull()
        }

    suspend fun get(): T = deferred.await()
}
