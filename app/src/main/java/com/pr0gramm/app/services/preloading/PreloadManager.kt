package com.pr0gramm.app.services.preloading

import com.pr0gramm.app.Instant
import rx.Observable
import java.io.File

/**
 */
interface PreloadManager {
    fun store(entry: PreloadItem)

    fun exists(itemId: Long): Boolean

    fun get(itemId: Long): PreloadItem?

    fun deleteBefore(threshold: Instant)

    fun all(): Observable<Collection<PreloadItem>>

    /**
     * A item that was preloaded.
     */
    class PreloadItem(val itemId: Long, val creation: Instant, val media: File, val thumbnail: File) {
        override fun hashCode(): Int = itemId.hashCode()

        override fun equals(other: Any?): Boolean {
            return other is PreloadItem && other.itemId == itemId
        }
    }
}
