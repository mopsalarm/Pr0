package com.pr0gramm.app.ui.views.viewer

import android.net.Uri
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.encodeBase64
import java.util.regex.Pattern

/**
 * Simple interface to the thumby service.
 */
object ThumbyService {
    private val RE_VALID_URI = Pattern.compile("^https?://[^/]*pr0gramm.com/.*")

    @JvmStatic
    fun thumbUri(mediaUri: MediaUri): Uri {
        if (BuildConfig.DEBUG) {
            require(isEligibleForPreview(mediaUri)) {
                "not eligible for thumby preview"
            }
        }

        // normalize url before fetching generated thumbnail
        val url = mediaUri.baseUri.toString()
                .replace("http://", "https://")
                .replace(".mpg", ".mp4")
                .replace(".webm", ".mp4")

        val encoded = url.toByteArray().encodeBase64(urlSafe = true)
        return Uri.parse("https://pr0.wibbly-wobbly.de/api/thumby/v1/$encoded/thumb.jpg")
    }


    /**
     * Return true, if the thumby service can produce a pixels for this url.
     * This is currently possible for gifs and videos.
     */
    @JvmStatic
    fun isEligibleForPreview(url: MediaUri): Boolean {
        val type = url.mediaType
        return (type == MediaUri.MediaType.VIDEO || type == MediaUri.MediaType.GIF)
                && RE_VALID_URI.matcher(url.baseUri.toString()).matches()
    }
}
