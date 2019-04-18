package com.pr0gramm.app.model.bookmark

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Bookmark(
        val title: String,

        // now optional fields, just here for migration
        val filterTags: String? = null,
        val filterUsername: String? = null,
        val filterFeedType: String? = null,

        val trending: Boolean = false,

        // new optional field for migrated bookmarks
        val link: String? = null)
