package com.pr0gramm.app.services

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pr0gramm.app.Logger
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.adapter
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.model.bookmark.Bookmark
import com.pr0gramm.app.orm.asFeedFilter
import com.pr0gramm.app.orm.migrate
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.getStringOrNull
import rx.Observable
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit

/**
 */
class BookmarkService(
        private val preferences: SharedPreferences,
        private val bookmarkSyncService: BookmarkSyncService) {

    private val logger = Logger("BookmarkService")
    private val bookmarks = BehaviorSubject.create<List<Bookmark>>()

    init {
        // restore previous json
        restoreFromSerialized(preferences.getStringOrNull("Bookmarks.json") ?: "[]")

        // serialize updates back to the preferences
        bookmarks.skip(1)
                .distinctUntilChanged()
                .debounce(100, TimeUnit.MILLISECONDS, Schedulers.computation())
                .subscribe { persist(it) }
    }

    /**
     * Fetches bookmarks from the remote api and publishes them locally.
     */
    suspend fun update() {
        val remote = bookmarkSyncService.fetch()

        synchronized(bookmarks) {
            // get the local ones that dont have a 'link' yet
            val local = bookmarks.value.filter { it.link == null }

            // and merge them together, with the local ones winning.
            val merged = (local + remote).distinctBy { it.title }

            bookmarks.onNext(merged)
        }
    }

    private fun restoreFromSerialized(json: String) {
        val bookmarks = MoshiInstance.adapter<List<Bookmark>>().fromJson(json) ?: listOf()

        logger.debug { "Restored ${bookmarks.size} bookmarks" }
        this.bookmarks.onNext(bookmarks)
    }

    private fun persist(bookmarks: List<Bookmark>) {
        logger.debug { "Persisting ${bookmarks.size} bookmarks to storage" }

        val json = MoshiInstance.adapter<List<Bookmark>>().toJson(bookmarks)
        preferences.edit { putString("Bookmarks.json", json) }

        doInBackground { uploadLocalBookmarks() }
    }

    suspend fun uploadLocalBookmarks() {
        val bookmarks = bookmarks.value

        // sync back all bookmarks that are local only (legacy bookmarks from the app)
        val localOnly = bookmarks.filter { it.link == null }

        if (localOnly.isNotEmpty() && bookmarkSyncService.isAuthorized) {
            logger.info { "Uploading all non default bookmarks now" }

            // get the names of all default bookmarks on the server side
            val defaults = bookmarkSyncService.fetch(anonymous = true).map { it.title }

            // and filter all bookmarks from the app that are not in the default bookmarks
            val custom = localOnly.filter { it.title !in defaults }

            // store those bookmarks on the remote side
            val remote = custom.map { bookmark -> bookmarkSyncService.add(bookmark) }.lastOrNull()

            // now all bookmarks are remote, we can publish them locally now.
            this.bookmarks.onNext(remote)
        }
    }

    /**
     * Returns "true", if the item is bookmarkable.
     */
    fun isBookmarkable(filter: FeedFilter): Boolean {
        if (!canEdit || filter.isBasic || filter.likes != null)
            return false

        return byFilter(filter) == null
    }

    /**
     * Observes change to the bookmarks
     */
    fun observe(): Observable<List<Bookmark>> {
        return bookmarks.map { it.sortedBy { it.title.toLowerCase() } }.distinctUntilChanged()
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

        updateAsync {
            // also delete bookmarks on the server
            bookmarkSyncService.delete(bookmark.title)
        }
    }

    private fun updateAsync(block: suspend () -> List<Bookmark>) {
        if (bookmarkSyncService.isAuthorized) {
            // do optimistic locking
            val currentState = this.bookmarks.value

            doInBackground {
                val updated = block()
                synchronized(this.bookmarks) {
                    if (this.bookmarks.value === currentState) {
                        this.bookmarks.onNext(updated)
                    }
                }
            }
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
            val values = bookmarks.value.toMutableList()

            // replace the first bookmark that has the same title
            val idx = values.indexOfFirst { it.title == bookmark.title }
            if (idx >= 0) {
                values[idx] = bookmark.migrate()
            } else {
                values.add(0, bookmark.migrate())
            }

            // dispatch changes
            bookmarks.onNext(values)
        }

        updateAsync {
            bookmarkSyncService.add(bookmark)
        }
    }

    suspend fun restore() {
        // get all default bookmarks
        val defaults = bookmarkSyncService.fetch(anonymous = true)

        // and save missing bookmarks remotely
        defaults.filter { byTitle(it.title) == null }.forEach { bookmark ->
            bookmarkSyncService.add(bookmark)
        }

        // then fetch the new remote list of bookmarks
        update()
    }

    val canEdit: Boolean get() = bookmarkSyncService.canChange
}