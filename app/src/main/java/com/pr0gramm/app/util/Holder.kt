package com.pr0gramm.app.util

import com.pr0gramm.app.ui.base.AsyncScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

/**
 * Holds a deferred value and provides access to the result.
 */
class Holder<T>(provider: suspend () -> T) {
    private val deferred = AsyncScope.async { provider() }

    /**
     * Gets the value of this holder. This will block, if the value
     * is not yet present.
     */
    val value: T get() = runBlocking { get() }

    /**
     * Returns the current value or returns null, if the value is
     * not yet available.
     */
    val valueOrNull: T? get() = runCatching { deferred.getCompleted() }.getOrNull()

    suspend fun get(): T = deferred.await()
}
