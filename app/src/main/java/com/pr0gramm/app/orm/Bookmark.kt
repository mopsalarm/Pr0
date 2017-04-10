package com.pr0gramm.app.orm

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.google.common.base.Objects.equal
import com.google.common.base.Optional
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.util.mapToList
import com.pr0gramm.app.util.toOptional
import org.slf4j.LoggerFactory

/**
 */
data class Bookmark(val title: String, private val filterTags: String?, private val filterUsername: String?, private val filterFeedType: String) {

    fun asFeedFilter(): FeedFilter {
        var filter = FeedFilter().withFeedType(FeedType.valueOf(filterFeedType))
        if (filterTags != null)
            filter = filter.withTags(filterTags)

        if (filterUsername != null)
            filter = filter.withUser(filterUsername)

        return filter
    }

    companion object {
        private val logger = LoggerFactory.getLogger("Bookmark")

        fun of(filter: FeedFilter, title: String): Bookmark {
            val filterTags = filter.tags.orNull()
            val filterUsername = filter.username.orNull()
            val filterFeedType = filter.feedType.toString()
            return Bookmark(title, filterTags, filterUsername, filterFeedType)
        }

        fun byFilter(database: SQLiteDatabase, filter: FeedFilter): Optional<Bookmark> {
            return Bookmark.all(database)
                    .firstOrNull { bookmark -> equal(filter, bookmark.asFeedFilter()) }
                    .toOptional()
        }

        fun save(db: SQLiteDatabase, bookmark: Bookmark) {
            val cv = ContentValues()
            cv.put("title", bookmark.title)
            cv.put("filter_tags", bookmark.filterTags)
            cv.put("filter_username", bookmark.filterUsername)
            cv.put("filter_feed_type", bookmark.filterFeedType)

            db.insert("bookmark", null, cv)
        }

        fun delete(db: SQLiteDatabase, bookmark: Bookmark) {
            db.delete("bookmark", "title=?", arrayOf(bookmark.title))
        }

        fun prepareDatabase(db: SQLiteDatabase) {
            logger.info("create table bookmark if not exists")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS bookmark (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    filter_feed_type TEXT,
                    filter_tags TEXT,
                    filter_username TEXT,
                    title TEXT
                )""")
        }

        fun all(database: SQLiteDatabase): List<Bookmark> {
            val query = "SELECT title, filter_tags, filter_username, filter_feed_type FROM bookmark ORDER BY title ASC"
            return database.rawQuery(query, null).mapToList {
                Bookmark(title = getString(0),
                        filterTags = getString(1),
                        filterUsername = getString(2),
                        filterFeedType = getString(3))
            }
        }
    }
}
