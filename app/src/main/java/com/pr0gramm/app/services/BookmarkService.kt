package com.pr0gramm.app.services

import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.core.content.edit
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.Settings
import com.pr0gramm.app.adapter
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.orm.Bookmark
import com.pr0gramm.app.util.*
import rx.Observable
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject


/**
 */
class BookmarkService(
        private val database: Holder<SQLiteDatabase>,
        private val preferences: SharedPreferences) {

    private val bookmarks = BehaviorSubject.create<List<Bookmark>>()

    init {
        // restore previous json
        restoreFromSerialized(preferences.getStringOrNull("Bookmarks.json") ?: "[]")

        // serialize updates back to the preferences
        bookmarks.skip(1).observeOn(Schedulers.computation()).subscribe { persist(it) }

        // migrate "old" data
        doInBackground { migrate() }
    }

    private fun restoreFromSerialized(json: String) = catchAll {
        val bookmarks = MoshiInstance.adapter<List<Bookmark>>().fromJson(json)
        this.bookmarks.onNext(bookmarks)
    }

    private fun persist(bookmarks: List<Bookmark>) {
        val json = MoshiInstance.adapter<List<Bookmark>>().toJson(bookmarks)
        preferences.edit { putString("Bookmarks.json", json) }
    }

    /**
     * Creates a bookmark for the filter.

     * @param filter The filter to create a bookmark for.
     */
    fun create(filter: FeedFilter, title: String) {
        save(Bookmark(title, filter))
    }

    /**
     * Returns an observable producing "true", if the item is bookmarkable.
     * The observable produces "false" otherwise.

     * @param filter The filter that the user wants to bookmark.
     */
    fun isBookmarkable(filter: FeedFilter): Boolean {
        if (filter.isBasic)
            return false

        if (filter.likes != null)
            return false

        return byFilter(filter) == null
    }

    /**
     * Observes change to the bookmarks
     */
    fun observe(): Observable<List<Bookmark>> {
        return bookmarks
    }

    /**
     * Deletes the given bookmark if it exists.
     */
    fun delete(bookmark: Bookmark) {
        synchronized(bookmarks) {
            // remove all matching bookmarks
            val newValues = bookmarks.value.filter { it != bookmark }
            bookmarks.onNext(newValues)
        }
    }

    /**
     * Returns a bookmark that has a filter equal to the queried one.
     */
    fun byFilter(filter: FeedFilter): Bookmark? {
        return bookmarks.value.firstOrNull { bookmark -> filter == bookmark.asFeedFilter() }
    }

    fun byTitle(title: String): Bookmark? {
        return bookmarks.value.firstOrNull { it.title == title }
    }

    /**
     * Save a bookmark to the database.
     */
    fun save(bookmark: Bookmark) {
        synchronized(bookmarks) {
            val exists = byTitle(bookmark.title) != null

            // add the new bookmark to the top
            val newValues = if (exists) {
                // replace the changed bookmark
                bookmarks.value.map { previous ->
                    if (previous.title == bookmark.title) {
                        bookmark
                    } else {
                        previous
                    }
                }
            } else {
                // add to the front
                listOf(bookmark) + bookmarks.value
            }

            // and deduplicate by title.
            bookmarks.onNext(newValues.distinctBy { it.title })
        }
    }

    /**
     * Query a list of all bookmarks directly from the database.
     */
    private suspend fun migrate() {
        if (Settings.get().legacyShowCategoryText) {
            // disable this for the next time
            Settings.get().edit {
                putBoolean("pref_show_category_text", false)
            }

            val filter = FeedFilter().withFeedType(FeedType.NEW).withTags("!'text'")
            save(Bookmark("Text in Neu", filter))
        }

        try {
            // get all values
            val query = "SELECT title, filter_tags, filter_username, filter_feed_type FROM bookmark ORDER BY title ASC"
            val bookmarks = database.get().rawQuery(query, null).use { cursor ->
                cursor.mapToList {
                    Bookmark(title = getString(0),
                            filterTags = getString(1),
                            filterUsername = getString(2),
                            filterFeedType = getString(3))
                }
            }

            // and delete all values from the table
            database.get().execSQL("DELETE FROM bookmark")

            // save all those bookmarks
            bookmarks.distinctBy { it.title }.forEach { save(it) }

        } catch (err: SQLiteException) {
            Logger("BookmarkService").info { "Could not migrate: $err" }
        }

        // check what else we need to change...
        synchronized(bookmarks) {
            val fixed = bookmarks.value.map { bookmark ->
                // migrate some values...
                val tags = bookmark.filterTags
                        ?.replace("webm", "video")
                        ?.replace("hat text", "text")
                        ?.replace("^'original content'", "! 'original content'")

                bookmark.copy(filterTags = tags)
            }

            bookmarks.onNext(fixed)
        }
    }
}