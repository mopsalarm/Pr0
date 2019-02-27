package com.pr0gramm.app.model.update

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateModel(val version: Int, val apk: String, val changelog: String)

@JsonClass(generateAdapter = true)
class Change(val type: String, val change: String)

@JsonClass(generateAdapter = true)
class ChangeGroup(val version: Int = 0, val changes: List<Change> = listOf())
