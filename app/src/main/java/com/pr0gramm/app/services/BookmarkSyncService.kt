package com.pr0gramm.app.services

import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.model.bookmark.Bookmark
import com.pr0gramm.app.orm.link
import com.pr0gramm.app.util.StringException
import java.util.Locale

class BookmarkSyncService(private val api: Api, private val userService: UserService) {
    private val logger = Logger("BookmarkSyncService")

    val isAuthorized get() = userService.isAuthorized

    val canChange get() = userService.isAuthorized && userService.userIsPremium

    suspend fun add(bookmark: Bookmark): List<Bookmark> {
        val title = bookmark.title

        val link = "/" + bookmark.link.trimStart('/')

        logger.info { "add bookmark '$title' ($link)" }
        val bookmarks = api.bookmarksAdd(null, title, link)
        return translate(bookmarks)
    }

    suspend fun fetch(anonymous: Boolean = !isAuthorized): List<Bookmark> {
        val bookmarks = if (anonymous) api.defaultBookmarks() else api.bookmarks()
        return translate(bookmarks)
    }

    suspend fun delete(title: String): List<Bookmark> {
        return translate(api.bookmarksDelete(null, title))
    }

    private fun translate(bookmarks: Api.Bookmarks): List<Bookmark> {
        val error = bookmarks.error
        if (error != null)
            throw StringException(error) { ctx -> ctx.getString(R.string.error_bookmark, error) }

        val normal = bookmarks.bookmarks.filterNot { isAppSpecialCategory(it) }.map { bookmarkOf(it, trending = false) }
        val trending = bookmarks.trending.sortedByDescending { it.velocity }.take(3).map { bookmarkOf(it, trending = true) }
        return normal + trending
    }

    /**
     * We might have better handling for those bookmarks in the app
     */
    private fun isAppSpecialCategory(bookmark: Api.Bookmark): Boolean {
        val name = bookmark.name.lowercase(Locale.GERMAN)
        return name == "best of" || name == "kontrovers" || name == "wichteln"
    }

    private fun bookmarkOf(b: Api.Bookmark, trending: Boolean): Bookmark {
        return Bookmark(b.name, _link = b.link, trending = trending)
    }
}