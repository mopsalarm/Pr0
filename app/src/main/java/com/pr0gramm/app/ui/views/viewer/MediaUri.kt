package com.pr0gramm.app.ui.views.viewer

import android.content.Context
import android.net.Uri
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.util.isLocalFile
import java.util.Locale

/**
 */
data class MediaUri(val id: Long, val baseUri: Uri, val mediaType: MediaType, val delay: Boolean = false) {
    fun withDelay(value: Boolean): MediaUri {
        return copy(delay = value)
    }

    val isLocalFile: Boolean = baseUri.isLocalFile

    override fun toString(): String {
        return baseUri.toString()
    }

    enum class MediaType {
        IMAGE, VIDEO, GIF
    }

    companion object {

        /**
         * Returns a media uri and guesses the media type from the uri.
         */

        fun of(id: Long, uri: Uri): MediaUri {
            val name = uri.lastPathSegment
                    ?: throw IllegalArgumentException("uri must have a file component")

            var type = MediaType.IMAGE
            if (name.lowercase(Locale.getDefault()).endsWith(".gif"))
                type = MediaType.GIF

            if (name.lowercase(Locale.getDefault()).matches(".*\\.(webm|mpe?g|mp4)".toRegex()))
                type = MediaType.VIDEO

            return MediaUri(id, uri, type)
        }


        fun of(id: Long, uri: String): MediaUri {
            return of(id, Uri.parse(uri))
        }


        fun of(context: Context, item: FeedItem): MediaUri {
            return of(item.id, UriHelper.of(context).media(item))
        }
    }
}
