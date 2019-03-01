package com.pr0gramm.app.model.inbox

import com.pr0gramm.app.Instant
import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class UnreadMarkerTimestamp(val id: String, val timestamp: Instant)
