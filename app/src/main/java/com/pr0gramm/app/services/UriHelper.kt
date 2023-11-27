package com.pr0gramm.app.services

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Thumbnail
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.services.preloading.PreloadManager
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.VideoQuality
import com.pr0gramm.app.util.di.injector


/**
 * A little helper class to work with URLs
 */
class UriHelper private constructor(private val context: Context) {
    private val preloadManager: PreloadManager by lazy { context.injector.instance() }

    fun thumbnail(item: Thumbnail): Uri {
        val preloaded = preloadManager[item.id]
        if (preloaded != null)
            return preloaded.thumbnail.toUri()

        return NoPreload.thumbnail(item)
    }

    fun fullThumbnail(item: Thumbnail): Uri {
        val preloaded = preloadManager[item.id]
        if (preloaded?.thumbnailFull != null)
            return preloaded.thumbnailFull.toUri()

        return NoPreload.thumbnail(item, full = true)
    }

    fun media(item: FeedItem, hq: Boolean = false): Uri {
        if (hq && item.fullsize.isNotEmpty()) {
            return NoPreload.media(item, highQuality = true)
        }

        val preloaded = preloadManager[item.id]
        if (preloaded != null) {
            return preloaded.media.toUri()
        }

        return NoPreload.media(
            item,
            mobile = AndroidUtility.isOnMobile(context),
            compatible = false,
            highQuality = hq,
            quality = Settings.videoQuality,
        )
    }

    fun image(id: Long, image: String): Uri {
        val preloaded = preloadManager[id]
        if (preloaded != null)
            return preloaded.media.toUri()

        return NoPreload.image(image)
    }

    fun base(): Uri {
        return start().build()
    }

    fun user(name: String): Uri {
        return start().appendPath("user").appendPath(name).build()
    }

    fun post(type: FeedType, itemId: Long): Uri {
        return start().path(FEED_TYPES[type])
            .appendPath(itemId.toString())
            .build()
    }

    fun post(type: FeedType, itemId: Long, commentId: Long): Uri {
        return start().path(FEED_TYPES[type])
            .appendEncodedPath("$itemId:comment$commentId")
            .build()
    }

    fun uploads(user: String): Uri {
        return start().path("/user/$user/uploads").build()
    }

    fun badgeImageUrl(image: String): Uri {
        if (image.startsWith("http://") || image.startsWith("https://")) {
            return Uri.parse(image)
        }

        if (image.startsWith("//")) {
            return Uri.parse("https:$image")
        }

        val builder = Uri.parse("https://pr0gramm.com/media/badges/").buildUpon()
        return if (image.startsWith("/")) {
            builder.path(image).build()
        } else {
            builder.appendEncodedPath(image).build()
        }
    }

    private fun start(): Uri.Builder {
        return Uri.Builder()
            .scheme("https")
            .authority("pr0gramm.com")
    }

    object NoPreload {
        fun mediaCompatible(item: FeedItem, highQuality: Boolean = false): Uri {
            return media(item, highQuality = highQuality, compatible = true)
        }

        fun media(
            item: FeedItem,
            highQuality: Boolean = false,
            mobile: Boolean = false,
            compatible: Boolean = false,
            quality: VideoQuality = VideoQuality.Adaptive,
        ): Uri {
            return if (highQuality && !item.isVideo && item.fullsize.isNotEmpty()) {
                absoluteJoin(start("full"), item.fullsize)
            } else {
                val path = item.pickVariant(quality, mobile, compatible).path
                absoluteJoin(start(if (item.isVideo) "vid" else "img"), path)
            }
        }

        fun image(image: String): Uri {
            return absoluteJoin(start("img"), image)
        }

        fun subtitle(subtitle: String): Uri {
            return absoluteJoin(start("img"), subtitle)
        }

        fun thumbnail(item: Thumbnail, full: Boolean = false): Uri {
            var path = item.thumbnail ?: ""
            if (full) {
                path = path.replace(".jpg", "-original.webp")
            }

            return absoluteJoin(start("thumb"), path)
        }

        private fun start(subdomain: String): Uri.Builder {
            return Uri.Builder()
                .scheme("https")
                .authority("$subdomain.pr0gramm.com")
        }
    }

    companion object {
        fun of(context: Context): UriHelper {
            return UriHelper(context)
        }

        private val FEED_TYPES = mapOf(
            FeedType.NEW to "new",
            FeedType.PROMOTED to "top",
            FeedType.STALK to "stalk"
        )

    }
}

private fun absoluteJoin(builder: Uri.Builder, path: String): Uri {
    if (path.startsWith("http://") || path.startsWith("https://")) {
        return Uri.parse(path)
    }

    if (path.startsWith("//")) {
        return Uri.parse("https:$path")
    }

    val normalized = if (path.startsWith("/")) path else "/$path"
    return builder.path(normalized).build()
}
