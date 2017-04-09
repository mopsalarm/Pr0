package com.pr0gramm.app.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri

import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.services.UriHelper

/**
 * Info about a pixels. Can be given to a post fragment to create
 * the fragment animation on newer versions of android.
 */
class PreviewInfo private constructor(val itemId: Long, val previewUri: Uri, val width: Int, val height: Int, val preview: Drawable? = null) {
    companion object {
        @JvmStatic
        @JvmOverloads
        fun of(context: Context, item: FeedItem, drawable: Drawable? = null): PreviewInfo {
            val thumbnail = UriHelper.of(context).thumbnail(item)
            return PreviewInfo(item.id(), thumbnail, item.width, item.height, drawable)
        }
    }
}
