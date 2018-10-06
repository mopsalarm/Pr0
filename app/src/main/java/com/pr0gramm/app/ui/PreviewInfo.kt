package com.pr0gramm.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.util.BackgroundScheduler
import com.pr0gramm.app.util.Holder
import rx.Observable

/**
 * Info about a pixels. Can be given to a post fragment to create
 * the fragment animation on newer versions of android.
 */
class PreviewInfo private constructor(val itemId: Long, val previewUri: Uri, val width: Int, val height: Int, val preview: Drawable? = null) {
    var fancy: Holder<Bitmap>? = null

    /**
     * Aspect ratio of the image this preview info represents.
     */
    val aspect: Float = width.toFloat() / height.toFloat()

    /**
     * You can pre-load a fancy thumbnail if you want to
     */
    fun preloadFancyPreviewImage(service: FancyExifThumbnailGenerator) {
        fancy = Holder.ofObservable(Observable
                .fromCallable { service.fancyThumbnail(previewUri, aspect) }
                .subscribeOn(BackgroundScheduler))
    }

    companion object {
        fun of(context: Context, item: FeedItem, drawable: Drawable? = null): PreviewInfo {
            val thumbnail = UriHelper.of(context).thumbnail(item)
            return PreviewInfo(item.id, thumbnail, item.width, item.height, drawable)
        }
    }
}
