package com.pr0gramm.app.api.pr0gramm

import com.pr0gramm.app.Instant

data class Message(
        val id: Long,
        val itemId: Long,
        val mark: Int,
        val message: String,
        val name: String,
        val score: Int,
        val senderId: Int,
        val read: Boolean,
        val creationTime: Instant,
        val thumbnail: String?,
        val type: String) {

    val isComment: Boolean get() = type == "comment"
    val commentId: Long get() = id
}