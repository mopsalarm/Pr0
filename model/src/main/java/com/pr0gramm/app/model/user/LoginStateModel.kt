package com.pr0gramm.app.model.user

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginState(
        val id: Int,
        val name: String?,
        val mark: Int,
        val score: Int,
        val uniqueToken: String?,
        val admin: Boolean,
        val premium: Boolean,
        val verified: Boolean = false,
        val authorized: Boolean)


@JsonClass(generateAdapter = true)
data class LoginCookie(
        val id: String,
        @Json(name = "n") val name: String,
        @Json(name = "paid") val paid: Boolean = false,
        @Json(name = "a") val admin: Boolean = false,
        @Json(name = "verified") val verified: Boolean = false,
)
