package com.pr0gramm.app.ui

import android.net.Uri
import android.os.Parcel
import android.text.style.URLSpan
import android.view.View
import com.pr0gramm.app.Settings
import com.pr0gramm.app.parcel.creator
import com.pr0gramm.app.util.BrowserHelper
import java.util.Locale

/**
 */
class PrivateBrowserSpan(url: String) : URLSpan(url) {
    override fun onClick(widget: View) {
        val url = url ?: "https://example.com"

        var useIncognitoBrowser = Settings.useIncognitoBrowser

        // check if youtube-links should be opened in normal app
        if (useIncognitoBrowser && Settings.overrideYouTubeLinks) {
            val host = Uri.parse(url).host
            if (host != null && BLACKLIST.contains(host.lowercase(Locale.ROOT)))
                useIncognitoBrowser = false
        }

        if (useIncognitoBrowser) {
            BrowserHelper.openIncognito(widget.context, url)
        } else {
            // dispatch link normally
            super.onClick(widget)
        }
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(url)
    }

    companion object {
        private val BLACKLIST = listOf(
                "youtube.com", "youtu.be", "www.youtube.com", "m.youtube.com",
                "vimeo.com")

        @JvmStatic
        val CREATOR = creator { parcel ->
            PrivateBrowserSpan(parcel.readString() ?: "https://example.com")
        }
    }
}
