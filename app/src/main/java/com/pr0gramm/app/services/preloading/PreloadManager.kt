package com.pr0gramm.app.services.preloading

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.collection.LongSparseArray
import com.pr0gramm.app.Instant
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.Databases.withTransaction
import com.squareup.sqlbrite.BriteDatabase
import rx.Observable
import java.io.File
import java.util.concurrent.TimeUnit


/**
 */

class PreloadManager(private val database: BriteDatabase) {
    @Volatile
    private var preloadCache = LongSparseArray<PreloadItem>()

    private val observeAllItems = Observable.interval(0, 10, TimeUnit.MINUTES)
            .switchMap {
                database
                        .createQuery(TABLE_NAME, "SELECT * FROM $TABLE_NAME")
                        .mapToList { cursor -> readPreloadItem(cursor) }
            }
            .map { items -> validateItems(items) }
            .share()

    init {
        // initialize and cache in background for polling
        observeAllItems.subscribe { preloadCache = it }
    }

    private fun validateItems(items: List<PreloadItem>): LongSparseArray<PreloadItem> {
        val missing = mutableListOf<PreloadItem>()
        val available = mutableListOf<PreloadItem>()

        // check if all files still exist
        for (item in items) {
            if (!item.thumbnail.exists() || !item.media.exists()) {
                missing += item
            } else {
                available += item
            }
        }

        if (missing.isNotEmpty()) {
            // delete missing entries in background.
            doInBackground {
                withTransaction(database) {
                    deleteTx(database, missing)
                }
            }
        }

        return longSparseArrayOf(available) { it.itemId }
    }

    private fun readPreloadItem(cursor: Cursor): PreloadItem {
        val cItemId = cursor.getColumnIndexOrThrow("itemId")
        val cCreation = cursor.getColumnIndexOrThrow("creation")
        val cMedia = cursor.getColumnIndexOrThrow("media")
        val cThumbnail = cursor.getColumnIndexOrThrow("thumbnail")

        return PreloadItem(
                itemId = cursor.getLong(cItemId),
                creation = Instant(cursor.getLong(cCreation)),
                media = File(cursor.getString(cMedia)),
                thumbnail = File(cursor.getString(cThumbnail)))
    }

    /**
     * Inserts the given entry blockingly into the database. Must not be run on the main thread.
     */
    fun store(entry: PreloadItem) {
        checkNotMainThread()

        val values = ContentValues()
        values.put("itemId", entry.itemId)
        values.put("creation", entry.creation.millis)
        values.put("media", entry.media.path)
        values.put("thumbnail", entry.thumbnail.path)
        database.insert(TABLE_NAME, values, SQLiteDatabase.CONFLICT_REPLACE)
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
        return preloadCache.get(itemId)
    }

    fun deleteOlderThan(threshold: Instant) {
        logger.info { "Removing all files preloaded before $threshold" }

        withTransaction(database) {
            val items = database.query("SELECT * FROM $TABLE_NAME WHERE creation<?",
                    threshold.millis.toString()).use { cursor ->

                cursor.mapToList { readPreloadItem(this) }
            }

            deleteTx(database, items)
        }
    }

    private fun deleteTx(db: BriteDatabase, items: Iterable<PreloadItem>) {
        for (item in items) {
            logger.info { "Removing files for itemId=${item.itemId}" }

            if (!item.media.delete())
                logger.warn { "Could not delete media file ${item.media}" }

            if (!item.thumbnail.delete())
                logger.warn { "Could not delete thumbnail file ${item.thumbnail}" }

            // delete entry from database
            db.delete(TABLE_NAME, "itemId=?", item.itemId.toString())
        }
    }

    val items: Observable<LongSparseArray<PreloadItem>> = observeAllItems.map { it.clone() }.share()

    /**
     * A item that was preloaded.
     */
    class PreloadItem(val itemId: Long, val creation: Instant, val media: File, val thumbnail: File) {
        override fun hashCode(): Int = itemId.hashCode()

        override fun equals(other: Any?): Boolean {
            return other is PreloadItem && other.itemId == itemId
        }
    }

    companion object {
        private val logger = Logger("PreloadManager")

        private const val TABLE_NAME = "preload_2"

        fun onCreate(db: SQLiteDatabase) {
            logger.info { "initializing sqlite database" }
            db.execSQL("""CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    itemId INT NOT NULL UNIQUE,
                    creation INT NOT NULL,
                    media TEXT NOT NULL,
                    thumbnail TEXT NOT NULL)""")
        }
    }
}
