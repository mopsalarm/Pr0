package com.pr0gramm.app.api.pr0gramm

import com.pr0gramm.app.feed.FeedItem

/**
 */
class Thumbnail(val id: Long, val thumbnail: String?)

fun Api.Message.asThumbnail(): Thumbnail {
    return Thumbnail(id, thumbnail)
}

fun Api.Posted.SimilarItem.asThumbnail(): Thumbnail {
    return Thumbnail(id, thumbnail)
}

fun FeedItem.asThumbnail(): Thumbnail {
    return Thumbnail(id, thumbnail)
}

