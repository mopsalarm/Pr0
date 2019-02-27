package com.pr0gramm.app.model.inbox

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class UnixSecondTimestamp(val id: String, val timestamp: Long)
