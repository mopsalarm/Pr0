package com.pr0gramm.app.services

import com.pr0gramm.app.Logger
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.model.bookmark.Bookmark
import com.pr0gramm.app.orm.migrate

class BookmarkSyncService(private val api: Api, private val userService: UserService) {
    private val logger = Logger("BookmarkSyncService")

    val isAuthorized get() = userService.isAuthorized

    suspend fun add(bookmark: Bookmark): List<Bookmark> {
        val title = bookmark.title
        val link = bookmark.migrate().link!!

        logger.info { "add bookmark '$title' ($link)" }
        val bookmarks = api.bookmarksAddAsync(null, title, link).await()
        return translate(bookmarks)
    }

    suspend fun fetch(anonymous: Boolean = !isAuthorized): List<Bookmark> {
        val def = if (anonymous) api.defaultBookmarksAsync() else api.bookmarksAsync()
        return translate(def.await())
    }

    suspend fun delete(title: String): List<Bookmark> {
        return translate(api.bookmarksDeleteAsync(null, title).await())
    }

    private fun translate(bookmarks: Api.Bookmarks): List<Bookmark> {
        return bookmarks.bookmarks.map { bookmarkOf(it) }
    }
}

fun bookmarkOf(b: Api.Bookmark): Bookmark {
    return Bookmark(b.name, link = b.link)
}