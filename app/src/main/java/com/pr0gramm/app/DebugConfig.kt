package com.pr0gramm.app

import okhttp3.HttpUrl.Companion.toHttpUrl

private val actualDebugConfig = DebugConfig(
        ignoreUnreadState = true
        // mockApiUrl = "https://1285d16b.eu.ngrok.io"
        // versionOverride = 100
)

var debugConfig = if (BuildConfig.DEBUG) actualDebugConfig else DebugConfig()

data class DebugConfig(
        val ignoreUnreadState: Boolean = false,
        val delayApiRequests: Boolean = false,
        val mockApiUrl: String? = null,
        val versionOverride: Int? = null) {

    val mockApiHost: String? = mockApiUrl?.toHttpUrl()?.host
}
