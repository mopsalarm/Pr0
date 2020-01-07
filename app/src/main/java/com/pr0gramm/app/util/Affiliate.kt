package com.pr0gramm.app.util

import android.util.Base64

object Affiliate {
    private val reHubTraffic = "(?:pornhub|redtube|tube8|youporn|xtube|spankwire|keezmovies|extremetube)\\.com".toRegex()

    private fun hubTraffic(url: String): String {
        val encoded = Base64.encodeToString(url.toByteArray(), Base64.URL_SAFE)
        return "https://app.pr0gramm.com/redirect.html?u=$encoded"
    }

    fun get(url: String): String? {
        return when {
            reHubTraffic.containsMatchIn(url) -> hubTraffic(url)

            // no affiliate url
            else -> null
        }
    }
}