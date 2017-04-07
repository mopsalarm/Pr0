package com.pr0gramm.app.services

import com.google.common.primitives.Longs
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem
import gnu.trove.set.TLongSet
import rx.Completable
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

/**
 */
@Singleton
class AdminService @Inject constructor(private val api: Api) {
    fun tagsDetails(itemId: Long): Observable<Api.TagDetails> {
        return api.tagDetails(itemId)
    }

    @JvmOverloads
    fun deleteItem(item: FeedItem, reason: String, notifyUser: Boolean, blockDays: Float? = null): Completable {
        val pNotifyUser = if (notifyUser) "on" else null
        val blockUser = if (blockDays != null && blockDays >= 0) "on" else null

        return api
                .deleteItem(null, item.id(), "custom", reason, pNotifyUser, blockUser, blockDays)
                .toCompletable()
    }

    fun deleteTags(itemId: Long, tagIds: TLongSet, blockDays: Float?): Completable {
        val pBlockUser = if (blockDays != null) "on" else null
        return api
                .deleteTag(null, itemId, pBlockUser, blockDays, Longs.asList(*tagIds.toArray()))
                .toCompletable()
    }

    companion object {
        val REASONS = listOf(
                "Repost",
                "Auf Anfrage",
                "Regel #1 - Bild unzureichend getagged (nsfw/nsfl)",
                "Regel #1 - Falsche/Sinnlose Nutzung des NSFP Filters",
                "Regel #2 - Gore/Porn/Suggestive Bilder mit Minderjährigen",
                "Regel #3 - Tierporn",
                "Regel #4 - Stumpfer Rassismus/Nazi-Nostalgie",
                "Regel #5 - Werbung/Spam",
                "Regel #6 - Infos zu Privatpersonen",
                "Regel #7 - Bildqualität",
                "Regel #8 - Ähnliche Bilder in Reihe",
                "Regel #12 - Warez/Logins zu Pay Sites",
                "Regel #14 - Screamer/Sound-getrolle",
                "Regel #15 - reiner Musikupload",
                "Trollscheiße.")
    }

}