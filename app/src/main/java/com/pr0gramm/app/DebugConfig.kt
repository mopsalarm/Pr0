package com.pr0gramm.app

import okhttp3.HttpUrl.Companion.toHttpUrl

private val actualDebugConfig = DebugConfig(
        versionOverride = 100
)

var debugConfig = if (BuildConfig.DEBUG) actualDebugConfig else DebugConfig()

data class DebugConfig(
        val mockApiUrl: String? = null,
        val versionOverride: Int? = null) {

    val mockApiHost: String? = mockApiUrl?.toHttpUrl()?.host
}
