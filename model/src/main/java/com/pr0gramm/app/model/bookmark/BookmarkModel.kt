package com.pr0gramm.app.model.bookmark

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Bookmark(
        val title: String,
        val filterTags: String?,
        val filterUsername: String?,
        val filterFeedType: String)
