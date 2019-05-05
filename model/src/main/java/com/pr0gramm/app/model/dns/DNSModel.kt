package com.pr0gramm.app.model.dns

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class DNSResponse(
        val Answer: List<DNSAnswer> = listOf())

@JsonClass(generateAdapter = true)
data class DNSAnswer(val type: Int, val data: String)