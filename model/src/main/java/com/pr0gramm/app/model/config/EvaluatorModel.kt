package com.pr0gramm.app.model.config

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class Range(val min: Double = 0.0, val max: Double = Double.MAX_VALUE)

@JsonClass(generateAdapter = true)
data class Rule(
        val key: String, val value: Any?,
        val versions: List<Range> = listOf(),
        val percentiles: List<Range> = listOf(),
        val times: List<Range> = listOf(),
        val beta: Boolean = false)