package com.pr0gramm.app.model.info

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
class InfoMessage(
        val message: String? = null,
        val messageId: String? = null,
        val endOfLife: Int?)