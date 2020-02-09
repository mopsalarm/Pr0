package com.pr0gramm.app.api.pr0gramm

import com.pr0gramm.app.Instant

enum class MessageType(val apiValue: String) {
    STALK("follows"),
    MESSAGE("message"),
    COMMENT("comment"),
    NOTIFICATION("notification");

    override fun toString(): String = apiValue

    companion object {
        val values = values().toList()
    }
}

data class Message(
        val id: Long,
        val itemId: Long,
        val message: String,
        val name: String,
        val creationTime: Instant,
        val image: String?,
        val thumbnail: String?,
        val type: MessageType,
        val mark: Int,
        val score: Int,
        val senderId: Int,
        val flags: Int,
        val read: Boolean) {

    val isComment: Boolean get() = type === MessageType.COMMENT
    val commentId: Long get() = id
}