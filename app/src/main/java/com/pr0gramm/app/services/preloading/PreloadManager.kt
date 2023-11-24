package com.pr0gramm.app.services.preloading

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.Logger
import com.pr0gramm.app.db.PreloadItemQueries
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.util.LongSparseArray
import com.pr0gramm.app.util.checkNotMainThread
import com.pr0gramm.app.util.delay
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.longSparseArrayOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.File


private fun intervalFlow(): Flow<Unit> {
    return flow {
        while (true) {
            emit(Unit)
            delay(Duration.minutes(10))
        }
    }
}

/**
 */

class PreloadManager(private val db: PreloadItemQueries) {
    private val logger = Logger("PreloadManager")

    @Volatile
    private var preloadCache = LongSparseArray<PreloadItem>()

    private val observeAllItems = intervalFlow()
        .flatMapLatest { db.all(this::mapper).asFlow().mapToList(Dispatchers.IO) }
            .map { items -> validateItems(items) }

    init {
        AsyncScope.launch {
            // initialize and cache in background for polling
            observeAllItems.collect { preloadCache = it }
        }
    }

    private fun mapper(itemId: Long, creationTime: Long, media: String, thumbnail: String, fullThumbnail: String?): PreloadItem {
        return PreloadItem(
                itemId = itemId,
                creation = Instant(creationTime),
                media = File(media),
                thumbnail = File(thumbnail),
                thumbnailFull = fullThumbnail?.let(::File)
        )
    }

    private suspend fun validateItems(items: List<PreloadItem>): LongSparseArray<PreloadItem> {
        val missing = mutableListOf<PreloadItem>()
        val available = mutableListOf<PreloadItem>()

        runInterruptible(Dispatchers.IO) {
            // check if all files still exist
            for (item in items) {
                if (!item.thumbnail.exists() || !item.media.exists()) {
                    missing += item
                } else {
                    available += item
                }
            }
        }

        if (missing.isNotEmpty()) {
            // delete missing entries in background.
            doInBackground { deleteItems(missing) }
        }

        return longSparseArrayOf(available) { it.itemId }
    }

    /**
     * Inserts the given entry blockingly into the database. Must not be run on the main thread.
     */
    fun store(entry: PreloadItem) {
        checkNotMainThread()

        db.save(entry.itemId, entry.creation.millis, entry.media.path, entry.thumbnail.path, entry.thumbnailFull?.path)
    }

    /**
     * Checks if an entry with the given itemId already exists in the database.
     */
    fun exists(itemId: Long): Boolean {
        return preloadCache.containsKey(itemId)
    }

    /**
     * Returns the [PreloadItem] with a given id.
     */
    operator fun get(itemId: Long): PreloadItem? {
        return preloadCache[itemId]
    }

    fun deleteOlderThan(threshold: Instant) {
        logger.info { "Removing all files preloaded before $threshold" }
        deleteItems(preloadCache.values().filter { it.creation < threshold })
    }

    private fun deleteItems(items: Iterable<PreloadItem>) {
        checkNotMainThread()

        db.transaction {
            for (item in items) {
                logger.info { "Removing files for itemId=${item.itemId}" }

                if (!item.media.delete()) {
                    logger.warn { "Could not delete media file ${item.media}" }
                }

                if (!item.thumbnail.delete()) {
                    logger.warn { "Could not delete thumbnail file ${item.thumbnail}" }
                }

                if (item.thumbnailFull?.delete() == false) {
                    logger.warn { "Could not delete thumbnail file ${item.thumbnailFull}" }
                }

                // delete entry from database
                db.deleteOne(item.itemId)
            }
        }
    }

    val currentItems: LongSparseArray<PreloadItem>
        get() = preloadCache.clone()

    val items: Flow<LongSparseArray<PreloadItem>> = observeAllItems

    /**
     * A item that was preloaded.
     */
    class PreloadItem(val itemId: Long, val creation: Instant, val media: File, val thumbnail: File, val thumbnailFull: File?) {
        override fun hashCode(): Int = itemId.hashCode()

        override fun equals(other: Any?): Boolean {
            return other is PreloadItem && other.itemId == itemId
        }
    }
}
