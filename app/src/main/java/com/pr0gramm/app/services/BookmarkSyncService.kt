package com.pr0gramm.app.services

import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.model.bookmark.Bookmark
import com.pr0gramm.app.orm.migrate
import com.pr0gramm.app.util.StringException

class BookmarkSyncService(private val api: Api, private val userService: UserService) {
    private val logger = Logger("BookmarkSyncService")

    val isAuthorized get() = userService.isAuthorized

    val canChange get() = userService.isAuthorized && userService.userIsPremium

    suspend fun add(bookmark: Bookmark): List<Bookmark> {
        val title = bookmark.title

        val link = "/" + bookmark.migrate().link!!.trimStart('/')

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
}

private fun translate(bookmarks: Api.Bookmarks): List<Bookmark> {
    if (bookmarks.error != null)
        throw StringException { ctx -> ctx.getString(R.string.error_bookmark, bookmarks.error) }

    return bookmarks.bookmarks.filterNot { isAppSpecialCategory(it) }.map { bookmarkOf(it) }
}

/**
 * We might have better handling for those bookmarks in the app
 */
private fun isAppSpecialCategory(bookmark: Api.Bookmark): Boolean {
    val name = bookmark.name.toLowerCase()
    return name == "best of" || name == "kontrovers" || name == "wichteln"
}

private fun bookmarkOf(b: Api.Bookmark): Bookmark {
    return Bookmark(b.name, link = b.link)
}