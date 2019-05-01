package com.pr0gramm.app.services

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pr0gramm.app.Logger
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.adapter
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.model.bookmark.Bookmark
import com.pr0gramm.app.orm.asFeedFilter
import com.pr0gramm.app.orm.isImmutable
import com.pr0gramm.app.orm.migrate
import com.pr0gramm.app.util.catchAll
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.getStringOrNull
import com.pr0gramm.app.util.tryEnumValueOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.sendBlocking
import rx.Observable
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit

/**
 */
class BookmarkService(
        private val preferences: SharedPreferences,
        private val userService: UserService,
        private val bookmarkSyncService: BookmarkSyncService) {

    private val logger = Logger("BookmarkService")
    private val bookmarks = BehaviorSubject.create<List<Bookmark>>(listOf())

    private val updateQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        // restore previous json
        restoreFromSerialized(preferences.getStringOrNull("Bookmarks.json") ?: "[]")

        // serialize updates back to the preferences
        bookmarks.skip(1)
                .distinctUntilChanged()
                .debounce(100, TimeUnit.MILLISECONDS, Schedulers.computation())
                .subscribe { persist(it) }

        userService.loginStates
                .debounce(100, TimeUnit.MILLISECONDS, Schedulers.computation())
                .subscribe { updateQueue.sendBlocking { update() } }

        doInBackground {
            for (action in updateQueue) {
                catchAll { action() }
            }
        }
    }

    /**
     * Fetches bookmarks from the remote api and publishes them locally.
     */
    suspend fun update() {
        mergeWith {
            bookmarkSyncService.fetch()
        }
    }

    private fun restoreFromSerialized(json: String) {
        val bookmarks = MoshiInstance.adapter<List<Bookmark>>().fromJson(json) ?: listOf()

        logger.debug { "Restored ${bookmarks.size} bookmarks" }
        this.bookmarks.onNext(bookmarks.filter { it.title.length <= 255 })
    }

    private fun persist(bookmarks: List<Bookmark>) {
        logger.debug { "Persisting ${bookmarks.size} bookmarks to storage" }

        val json = MoshiInstance.adapter<List<Bookmark>>().toJson(bookmarks)
        preferences.edit { putString("Bookmarks.json", json) }

        updateQueue.sendBlocking { uploadLocalBookmarks() }
    }

    suspend fun uploadLocalBookmarks() {
        val bookmarks = bookmarks.value

        // sync back all bookmarks that are not yet synchronized (legacy bookmarks from the app)
        val notSynchronized = bookmarks.filter { it.requiresSync }

        if (notSynchronized.isNotEmpty() && bookmarkSyncService.isAuthorized) {
            logger.info { "Uploading all non default bookmarks now" }

            // get the names of all default bookmarks on the server side
            val defaults = bookmarkSyncService.fetch(anonymous = true).map { it.title.toLowerCase() }

            // and filter all bookmarks from the app that are not in the default bookmarks
            val custom = notSynchronized.filter { it.title.toLowerCase() !in defaults }

            // store those bookmarks on the remote side
            val remote = custom.map { bookmark -> bookmarkSyncService.add(bookmark) }.lastOrNull()

            mergeCurrentState(remote ?: listOf())
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
        val comparator = compareBy<Bookmark> { !it.trending }.thenBy { it.title.toLowerCase() }
        return bookmarks.map { it.sortedWith(comparator) }.distinctUntilChanged()
    }

    /**
     * Deletes the given bookmark if it exists. Bookmarks are compared by their title.
     */
    fun delete(bookmark: Bookmark) {
        synchronized(bookmarks) {
            // remove all matching bookmarks
            val newValues = bookmarks.value.filterNot { it.hasTitle(bookmark.title) }
            bookmarks.onNext(newValues)
        }

        if (bookmarkSyncService.isAuthorized && !bookmark.localOnly) {
            mergeWithAsync {
                // also delete bookmarks on the server
                bookmarkSyncService.delete(bookmark.title)
            }
        }
    }

    private fun mergeWithAsync(block: suspend () -> List<Bookmark>) {
        updateQueue.sendBlocking { mergeWith(block) }
    }

    private suspend fun mergeWith(block: suspend () -> List<Bookmark>) {
        // do optimistic locking
        val currentState = this.bookmarks.value

        val updated = block()
        synchronized(this.bookmarks) {
            if (this.bookmarks.value === currentState) {
                mergeCurrentState(updated)
            }
        }
    }

    private fun mergeCurrentState(update: List<Bookmark>) {
        synchronized(bookmarks) {
            // get the local ones that dont have a 'link' yet
            val local = bookmarks.value.filter { it.link == null || it.localOnly }

            // and merge them together, with the local ones winning.
            val merged = (local + update).distinctBy { it.title.toLowerCase() }

            bookmarks.onNext(merged)
        }
    }

    /**
     * Returns a bookmark that has a filter equal to the queried one.
     */
    fun byFilter(filter: FeedFilter): Bookmark? {
        return bookmarks.value.firstOrNull { bookmark -> filter == bookmark.asFeedFilter() }
    }

    fun byTitle(title: String): Bookmark? {
        return bookmarks.value.firstOrNull { it.hasTitle(title) }
    }

    /**
     * Save a bookmark to the database.
     */
    fun save(bookmark: Bookmark) {
        rename(bookmark, bookmark.title)
    }

    /**
     * Rename a bookmark
     */
    fun rename(existing: Bookmark, newTitle: String) {
        val new = existing.copy(title = newTitle).let { if (it.localOnly) it else it.migrate() }

        synchronized(bookmarks) {
            val values = bookmarks.value.toMutableList()

            // replace the first bookmark that has the old title,
            // or add the new bookmark to the end
            val idx = values.indexOfFirst { it.hasTitle(existing.title) }
            if (idx >= 0) {
                values[idx] = new
            } else {
                values.add(0, new)
            }

            // dispatch changes
            bookmarks.onNext(values)
        }

        if (bookmarkSyncService.isAuthorized) {
            if (new.localOnly) {
                if (existing.title != newTitle) {
                    mergeWithAsync {
                        bookmarkSyncService.delete(existing.title)
                    }
                }
            } else {
                mergeWithAsync {
                    if (existing.title != newTitle) {
                        bookmarkSyncService.delete(existing.title)
                    }

                    bookmarkSyncService.add(new)
                }
            }
        }
    }

    suspend fun restore() {
        // get all default bookmarks
        val defaults = bookmarkSyncService.fetch(anonymous = true).filter { !it.isImmutable }

        // and save missing bookmarks remotely
        defaults.filter { byTitle(it.title) == null }.forEach { bookmark ->
            bookmarkSyncService.add(bookmark)
        }

        // then fetch the new remote list of bookmarks
        update()
    }

    val canEdit: Boolean get() = bookmarkSyncService.canChange

    private fun Bookmark.hasTitle(title: String): Boolean {
        return this.title.equals(title, ignoreCase = false)
    }

    private val Bookmark.localOnly: Boolean
        get() {
            val feedType = tryEnumValueOf<FeedType>(filterFeedType)
            if (feedType != null && feedType !in listOf(FeedType.NEW, FeedType.PROMOTED))
                return true

            val link = this.migrate().link
            return title.length >= 255 && link != null && link.length >= 255
        }

    private val Bookmark.requiresSync: Boolean get() = link == null && !localOnly
}