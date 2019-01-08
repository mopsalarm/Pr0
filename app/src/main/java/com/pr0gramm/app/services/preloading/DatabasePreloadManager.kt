package com.pr0gramm.app.services.preloading

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.pr0gramm.app.Instant
import com.pr0gramm.app.services.preloading.PreloadManager.PreloadItem
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.Databases.withTransaction
import com.squareup.sqlbrite.BriteDatabase
import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import rx.Observable
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicReference


/**
 */

class DatabasePreloadManager(private val database: BriteDatabase) : PreloadManager {
    private val preloadCache = AtomicReference(
            TLongObjectHashMap<PreloadItem>() as TLongObjectMap<PreloadItem>)

    private val observeAllItems = Observable.just(database)
            .flatMap { db ->
                db.createQuery(TABLE_NAME, QUERY_ALL_ITEM_IDS)
                        .mapToList { readPreloadItem(it) }
                        .map { convertToMap(it) }
                        .onErrorResumeEmpty()
            }
            .share()

    init {
        // initialize and cache in background for polling
        observeAllItems.subscribe { setPreloadCache(it) }
    }

    private fun setPreloadCache(items: TLongObjectMap<PreloadItem>) {
        val missing = mutableListOf<PreloadItem>()
        val available = TLongObjectHashMap<PreloadItem>(items.size())

        for (item in items.valueCollection()) {
            if (!item.thumbnail.exists() || !item.media.exists()) {
                missing.add(item)
            } else {
                available.put(item.itemId, item)
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

        preloadCache.set(available)
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

    private fun convertToMap(items: List<PreloadItem>): TLongObjectMap<PreloadItem> {
        val map = TLongObjectHashMap<PreloadItem>(items.size)
        items.forEach { item -> map.put(item.itemId, item) }
        return map
    }

    /**
     * Inserts the given entry blockingly into the database. Must not be run on the main thread.
     */
    override fun store(entry: PreloadItem) {
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
    override fun exists(itemId: Long): Boolean {
        return preloadCache.get().containsKey(itemId)
    }

    /**
     * Returns the [PreloadItem] with a given id.
     */
    override operator fun get(itemId: Long): PreloadItem? {
        return preloadCache.get().get(itemId)
    }

    override fun deleteBefore(threshold: Instant) {
        logger.info { "Removing all files preloaded before $threshold" }

        withTransaction(database) {
            val items = ArrayList<PreloadItem>()
            database.query("SELECT * FROM $TABLE_NAME WHERE creation<?",
                    threshold.millis.toString()).use { cursor ->

                while (cursor.moveToNext())
                    items.add(readPreloadItem(cursor))
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

    /**
     * Returns a list of all preloaded items.
     */
    override fun all(): Observable<Collection<PreloadItem>> {
        return observeAllItems.map { it.valueCollection() }
    }

    companion object {
        private val logger = Logger("DatabasePreloadManager")

        private const val TABLE_NAME = "preload_2"
        private const val QUERY_ALL_ITEM_IDS = "SELECT * FROM $TABLE_NAME"

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
