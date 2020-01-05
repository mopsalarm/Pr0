package com.pr0gramm.app.util

import android.net.Uri

object Affiliate {
    private val paramsHubTraffic = mapOf(
            "utm_source" to "paid",
            "utm_medium" to "hubtraffic",
            "utm_campaign" to "hubtraffic_pr0grammapp")

    private val reHubTraffic = "(?:pornhub|redtube|tube8|youporn|xtube|spankwire|keezmovies|extremetube)\\.com".toRegex()

    private fun hubTraffic(url: String): String {
        val uri = Uri.parse(url)

        val updated = uri.buildUpon().clearQuery()

        for (name in uri.queryParameterNames - paramsHubTraffic.keys) {
            val value = uri.getQueryParameter(name) ?: continue
            updated.appendQueryParameter(name, value)
        }

        for ((name, value) in paramsHubTraffic) {
            updated.appendQueryParameter(name, value)
        }

        return updated.build().toString()
    }

    fun get(url: String): String? {
        return when {
            reHubTraffic.containsMatchIn(url) -> hubTraffic(url)

            // no affiliate url
            else -> null
        }
    }
}