package com.pr0gramm.app.services

import android.database.sqlite.SQLiteDatabase
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.orm.Bookmark
import com.pr0gramm.app.ui.base.toObservable
import com.pr0gramm.app.util.BackgroundScheduler
import com.pr0gramm.app.util.Holder
import com.pr0gramm.app.util.checkNotMainThread
import com.pr0gramm.app.util.doInBackground
import rx.Completable
import rx.Observable
import rx.subjects.BehaviorSubject


/**
 */
class BookmarkService(private val database: Holder<SQLiteDatabase>) {

    private val onChange = BehaviorSubject.create<Void>(null as Void?).toSerialized()

    /**
     * Creates a bookmark for the filter.

     * @param filter The filter to create a bookmark for.
     */
    suspend fun create(filter: FeedFilter, title: String) {
        // check if here is an existing item
        Bookmark.byFilter(database.value, filter) ?: run {
            // create new entry
            Bookmark.save(database.value, Bookmark.of(filter, title))
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

        // check if already in database
        return database.valueOrNull?.let { db -> Bookmark.byFilter(db, filter) == null } == true
    }

    private fun triggerChange() {
        onChange.onNext(null)
    }

    fun get(): Observable<List<Bookmark>> {
        return onChange.observeOn(BackgroundScheduler).flatMap { toObservable(this::list) }
    }

    /**
     * Blockingly list the bookmarks, ordered by title.

     * @return The current bookmarks
     */
    private suspend fun list(): List<Bookmark> {
        checkNotMainThread()
        return Bookmark.all(database.value)
    }

    /**
     * Delete the bookmark. This method will not block.

     * @param bookmark The bookmark that is to be deleted.
     */
    suspend fun delete(bookmark: Bookmark) {
        Bookmark.delete(database.value, bookmark)
        triggerChange()
    }
}