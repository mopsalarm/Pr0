package com.pr0gramm.app.orm

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.util.logger
import com.pr0gramm.app.util.mapToList

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
        private val logger = logger("Bookmark")

        fun of(filter: FeedFilter, title: String): Bookmark {
            val filterTags = filter.tags
            val filterUsername = filter.username
            val filterFeedType = filter.feedType.toString()
            return Bookmark(title, filterTags, filterUsername, filterFeedType)
        }

        fun byFilter(database: SQLiteDatabase, filter: FeedFilter): Bookmark? {
            return Bookmark.all(database)
                    .firstOrNull { bookmark -> filter == bookmark.asFeedFilter() }
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
            logger.info { "create table bookmark if not exists" }
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

            val bookmarks = database.rawQuery(query, null).mapToList {
                Bookmark(title = getString(0),
                        filterTags = getString(1),
                        filterUsername = getString(2),
                        filterFeedType = getString(3))
            }

            return bookmarks.map { bookmark ->
                // i fucked up, so lets add hacky code to fix my mistake
                if (bookmark.filterTags == "'original content'") {
                    bookmark.copy(filterTags = "! 'original content'")
                }

                bookmark
            }
        }
    }
}
