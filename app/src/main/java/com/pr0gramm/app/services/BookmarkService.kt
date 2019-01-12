package com.pr0gramm.app.services

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.orm.Bookmark
import com.pr0gramm.app.ui.base.toObservable
import com.pr0gramm.app.ui.fragments.FeedFragment.Companion.logger
import com.pr0gramm.app.util.Holder
import com.pr0gramm.app.util.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rx.Observable
import rx.subjects.BehaviorSubject


/**
 */
class BookmarkService(private val database: Holder<SQLiteDatabase>) {
    private val onChange = BehaviorSubject.create<Unit>(Unit).toSerialized()

    /**
     * Creates a bookmark for the filter.

     * @param filter The filter to create a bookmark for.
     */
    suspend fun create(filter: FeedFilter, title: String) {
        // check if here is an existing item
        byFilter(filter) ?: run {
            // create new entry
            val filterTags = filter.tags
            val filterUsername = filter.username
            val filterFeedType = filter.feedType.toString()
            save(Bookmark(title, filterTags, filterUsername, filterFeedType))

            triggerChange()
        }
    }

    /**
     * Returns an observable producing "true", if the item is bookmarkable.
     * The observable produces "false" otherwise.

     * @param filter The filter that the user wants to bookmark.
     */
    suspend fun isBookmarkable(filter: FeedFilter): Boolean {
        if (filter.isBasic)
            return false

        if (filter.likes != null)
            return false

        return !exists(filter)
    }

    private fun triggerChange() {
        onChange.onNext(Unit)
    }

    /**
     * Observes change to the bookmarks
     */
    fun observe(): Observable<List<Bookmark>> {
        return onChange.flatMap { toObservable(block = this::queryAll) }
    }

    /**
     * Deletes the given bookmark if it exists.
     */
    suspend fun delete(bookmark: Bookmark) {
        withContext(Dispatchers.IO) {
            database.get().delete("bookmark", "title=?", arrayOf(bookmark.title))
        }

        triggerChange()
    }

    /**
     * Deletes the bookmark with the given filter value.
     */
    suspend fun delete(filter: FeedFilter) {
        byFilter(filter)?.let { delete(it) }
    }

    /**
     * True if a bookmark for this filter exists.
     */
    suspend fun exists(filter: FeedFilter): Boolean {
        return byFilter(filter) != null
    }

    /**
     * Returns a bookmark that has a filter equal to the queried one.
     */
    private suspend fun byFilter(filter: FeedFilter): Bookmark? {
        return queryAll().firstOrNull { bookmark -> filter == bookmark.asFeedFilter() }
    }

    /**
     * Save a bookmark to the database.
     */
    private suspend fun save(bookmark: Bookmark) {
        withContext(Dispatchers.IO) {
            val cv = ContentValues()
            cv.put("title", bookmark.title)
            cv.put("filter_tags", bookmark.filterTags)
            cv.put("filter_username", bookmark.filterUsername)
            cv.put("filter_feed_type", bookmark.filterFeedType)

            database.get().insert("bookmark", null, cv)
        }
    }

    /**
     * Query a list of all bookmarks directly from the database.
     */
    private suspend fun queryAll(): List<Bookmark> {
        val query = "SELECT title, filter_tags, filter_username, filter_feed_type FROM bookmark ORDER BY title ASC"

        return withContext(Dispatchers.IO) {
            val bookmarks = database.get().rawQuery(query, null).use { cursor ->
                cursor.mapToList {
                    Bookmark(title = getString(0),
                            filterTags = getString(1),
                            filterUsername = getString(2),
                            filterFeedType = getString(3))
                }
            }

            bookmarks.map { bookmark ->
                // i fucked up, so lets add hacky code to fix my mistake
                if (bookmark.filterTags == "'original content'") {
                    bookmark.copy(filterTags = "! 'original content'")
                }

                bookmark
            }
        }
    }

    companion object {
        fun prepare(db: SQLiteDatabase) {
            logger.info { "Create table bookmark if not exists" }
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS bookmark (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    filter_feed_type TEXT,
                    filter_tags TEXT,
                    filter_username TEXT,
                    title TEXT)""")
        }
    }
}