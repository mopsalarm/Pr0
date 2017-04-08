package com.pr0gramm.app.services.preloading

import com.google.common.base.Optional
import org.joda.time.Instant
import rx.Observable
import java.io.File

/**
 */
interface PreloadManager {
    fun store(entry: PreloadItem)

    fun exists(itemId: Long): Boolean

    fun get(itemId: Long): Optional<PreloadItem>

    fun deleteBefore(threshold: Instant)

    fun all(): Observable<Collection<PreloadItem>>

    /**
     * A item that was preloaded.
     */
    class PreloadItem(val itemId: Long, val creation: Instant, val media: File, val thumbnail: File)
}
