package com.pr0gramm.app.model.sitesettings

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SiteSettings(
    val themeId: Int,
    val showAds: Boolean,
    val favUpvote: Boolean,

    @Json(name = "legacyPath")
    val secondaryServers: Boolean,

    val enableItemHistory: Boolean,
    var markSeenItems: Boolean,
)
