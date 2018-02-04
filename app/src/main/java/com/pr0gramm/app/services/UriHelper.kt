package com.pr0gramm.app.services

import android.content.Context
import android.net.Uri
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import com.google.common.base.Strings
import com.pr0gramm.app.HasThumbnail
import com.pr0gramm.app.Settings
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.services.config.Config
import com.pr0gramm.app.services.preloading.PreloadManager
import com.pr0gramm.app.util.toUri


/**
 * A little helper class to work with URLs
 */
class UriHelper private constructor(context: Context, private val forceHttps: Boolean) {
    private val settings: Settings = Settings.get()
    private val noPreload = NoPreload()

    private val preloadManager: PreloadManager by lazy { context.appKodein().instance<PreloadManager>() }

    private fun start(): Uri.Builder {
        return Uri.Builder()
                .scheme(scheme())
                .authority("pr0gramm.com")
    }

    private fun scheme(): String {
        return if (forceHttps || settings.useSSL) "https" else "http"
    }

    internal fun start(subdomain: String): Uri.Builder {
        return Uri.Builder()
                .scheme(scheme())
                .authority(subdomain + ".pr0gramm.com")
    }

    fun thumbnail(item: HasThumbnail): Uri {
        return preloadManager.get(item.id())?.thumbnail?.toUri() ?: noPreload.thumbnail(item)
    }

    @JvmOverloads
    fun media(item: FeedItem, hq: Boolean = false): Uri {
        if (hq && !Strings.isNullOrEmpty(item.fullsize))
            return noPreload.media(item, true)

        return preloadManager.get(item.id())?.media?.toUri() ?: noPreload.media(item, false)
    }

    fun media(path: String): Uri {
        val isVideo = path.endsWith(".mp4") || path.endsWith(".webm")
        return absoluteJoin(start(if (isVideo) "vid" else "img"), path)
    }

    fun base(): Uri {
        return start().build()
    }

    fun post(type: FeedType, itemId: Long): Uri {
        return start().path(FEED_TYPES[type])
                .appendPath(itemId.toString())
                .build()
    }

    fun post(type: FeedType, itemId: Long, commentId: Long): Uri {
        return start().path(FEED_TYPES[type])
                .appendEncodedPath(itemId.toString() + ":comment" + commentId)
                .build()
    }

    fun uploads(user: String): Uri {
        return start().path("/user/$user/uploads").build()
    }

    fun favorites(user: String): Uri {
        return start().path("/user/$user/likes").build()
    }

    fun noPreload(): NoPreload {
        return noPreload
    }

    inner class NoPreload internal constructor() {
        fun media(item: FeedItem): Uri {
            return media(item, false)
        }

        fun media(item: FeedItem, highQuality: Boolean): Uri {
            return if (highQuality && !item.isVideo)
                absoluteJoin(start("full"), item.fullsize)
            else
                absoluteJoin(start(if (item.isVideo) "vid" else "img"), item.image)
        }

        fun thumbnail(item: HasThumbnail): Uri {
            return absoluteJoin(start("thumb"), item.thumbnail() ?: "")
        }
    }

    private fun absoluteJoin(builder: Uri.Builder, path: String): Uri {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return Uri.parse(path)
        }

        if (path.startsWith("//")) {
            return Uri.parse(scheme() + ":" + path)
        }

        val normalized = if (path.startsWith("/")) path else "/" + path
        return builder.path(normalized).build()
    }

    private fun join(builder: Uri.Builder, path: String): Uri {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return Uri.parse(path)
        }

        if (path.startsWith("//")) {
            return Uri.parse(scheme() + ":" + path)
        }

        if (path.startsWith("/")) {
            return builder.path(path).build()
        } else {
            return builder.appendEncodedPath(path).build()
        }
    }

    companion object {
        fun of(context: Context, forceSSL: Boolean = false): UriHelper {
            val configForceSSL = context.appKodein().instance<Config>().forceSSL
            return UriHelper(context, configForceSSL || forceSSL)
        }

        private val FEED_TYPES = mapOf(
                FeedType.NEW to "new",
                FeedType.PROMOTED to "top",
                FeedType.PREMIUM to "stalk")
    }

    fun badgeImageUrl(image: String): Uri {
        val builder = Uri.parse("http://pr0gramm.com/media/badges/").buildUpon()
        return join(builder, image)
    }
}
