package com.pr0gramm.app.services

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pr0gramm.app.Logger
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.model.bookmark.Bookmark
import com.pr0gramm.app.orm.asFeedFilter
import com.pr0gramm.app.orm.isImmutable
import com.pr0gramm.app.orm.link
import com.pr0gramm.app.orm.migrate
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.util.catchAll
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.getStringOrNull
import com.pr0gramm.app.util.tryEnumValueOf
import com.squareup.moshi.adapter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

/**
 */
class BookmarkService(
        private val preferences: SharedPreferences,
        private val userService: UserService,
        private val bookmarkSyncService: BookmarkSyncService) {

    private val logger = Logger("BookmarkService")
    private val bookmarks = MutableStateFlow<List<Bookmark>>(listOf())

    private val updateQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    val all: List<Bookmark>
        get() = bookmarks.value

    init {
        // restore previous json
        restoreFromSerialized(preferences.getStringOrNull("Bookmarks.json") ?: "[]")

        AsyncScope.launch {
            // serialize updates back to the preferences
            bookmarks.drop(1)
                    .distinctUntilChanged()
                    .debounce(100)
                    .collect { persist(it) }
        }

        AsyncScope.launch {
            userService.loginStates
                    .debounce(100)
                    .collect { updateQueue.send { update() } }
        }

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
        this.bookmarks.value = bookmarks.filter { it.title.length <= 255 }
    }

    private fun persist(bookmarks: List<Bookmark>) {
        logger.debug { "Persisting ${bookmarks.size} bookmarks to storage" }

        val json = MoshiInstance.adapter<List<Bookmark>>().toJson(bookmarks)
        preferences.edit { putString("Bookmarks.json", json) }
    }

    /**
     * Returns "true", if the item is bookmarkable.
     */
    fun isBookmarkable(filter: FeedFilter): Boolean {
        if (!canEdit || filter.isBasic || filter.collection != null)
            return false

        return byFilter(filter) == null
    }

    /**
     * Observes change to the bookmarks
     */
    fun observe(): Flow<List<Bookmark>> {
        val comparator = compareBy<Bookmark> { !it.trending }.thenBy { it.title.lowercase(Locale.getDefault()) }
        return bookmarks.map { it.sortedWith(comparator) }.distinctUntilChanged()
    }

    /**
     * Deletes the given bookmark if it exists. Bookmarks are compared by their title.
     */
    fun delete(bookmark: Bookmark) {
        synchronized(bookmarks) {
            // remove all matching bookmarks
            val newValues = bookmarks.value.filterNot { it.hasTitle(bookmark.title) }
            bookmarks.value = newValues
        }

        if (bookmarkSyncService.isAuthorized && bookmark.syncable) {
            mergeWithAsync {
                // also delete bookmarks on the server
                bookmarkSyncService.delete(bookmark.title)
            }
        }
    }

    private fun mergeWithAsync(block: suspend () -> List<Bookmark>) {
        updateQueue.trySendBlocking { mergeWith(block) }
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
            val local = bookmarks.value.filter { it._link == null || it.notSyncable }

            // and merge them together, with the local ones winning.
            val merged = (local + update).distinctBy { it.title.lowercase(Locale.getDefault()) }

            bookmarks.value = merged
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
        val new = existing.copy(title = newTitle).let { if (it.notSyncable) it else it.migrate() }

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
            bookmarks.value = values
        }

        if (bookmarkSyncService.isAuthorized) {
            if (new.notSyncable) {
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

    private val Bookmark.syncable: Boolean
        get() {
            // can not be synced if feed type is e.g. RANDOM, CONTROVERSIAL, etc
            val feedType = tryEnumValueOf<FeedType>(filterFeedType)
            if (feedType != null && feedType !in listOf(FeedType.NEW, FeedType.PROMOTED))
                return false

            // can only by synced if strings are short.
            val link = this.link
            return title.length < 255 && link.length < 255
        }

    private val Bookmark.notSyncable: Boolean get() = !syncable
}