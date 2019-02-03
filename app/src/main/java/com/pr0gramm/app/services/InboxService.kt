package com.pr0gramm.app.services

import android.content.SharedPreferences
import androidx.collection.LruCache
import androidx.core.content.edit
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.util.Logger
import com.pr0gramm.app.util.StringException
import com.pr0gramm.app.util.getObject
import com.pr0gramm.app.util.setObject
import com.squareup.moshi.JsonClass
import rx.Observable
import rx.subjects.BehaviorSubject


/**
 * Service for receiving and sending private messages
 * as well as the combined inbox (comments and private messages)
 */
class InboxService(private val api: Api, private val preferences: SharedPreferences) {
    private val logger = Logger("InboxService")

    private val unreadMessagesCount = BehaviorSubject.create<Api.InboxCounts>().toSerialized()!!

    private val readUpToCache = LruCache<String, Instant>(128)

    @JsonClass(generateAdapter = true)
    data class Timestamp(val id: String, val timestamp: Instant)

    init {
        // restore previous cache entries
        val readUpToTimestamps: List<Timestamp> = preferences
                .getObject("InboxService.readUpTo") ?: listOf()

        readUpToTimestamps.forEach { readUpToCache.put(it.id, it.timestamp) }
    }

    private fun persistState() {
        val values = readUpToCache.snapshot().map { entry -> Timestamp(entry.key, entry.value) }
        preferences.edit {
            setObject("InboxService.readUpTo", values)
        }
    }

    /**
     * Gets unread messages
     */
    suspend fun pending(): List<Api.Message> {
        return api.inboxPendingAsync().await().messages
    }

    /**
     * Gets the list of inbox comments
     */
    suspend fun comments(olderThan: Instant? = null): List<Api.Message> {
        val comments = api.inboxCommentsAsync(olderThan?.epochSeconds).await().messages

        // mark the most recent comment as read.
        comments.maxBy { it.creationTime }?.let { markAsRead(it) }

        return comments
    }

    /**
     * Gets all the comments a user has written
     */
    suspend fun getUserComments(user: String, contentTypes: Set<ContentType>, olderThan: Instant? = null): Api.UserComments {
        val beforeInSeconds = (olderThan ?: Instant.now().plus(Duration.days(1))).epochSeconds
        return api.userCommentsAsync(user, beforeInSeconds, ContentType.combine(contentTypes)).await()
    }

    fun markAsRead(notifyId: String, timestamp: Instant) {
        if (messageIsUnread(notifyId, timestamp)) {
            logger.debug {
                "Mark all messages for id=$notifyId with " +
                        "timestamp less than or equal to $timestamp as read"
            }

            readUpToCache.put(notifyId, timestamp)
            persistState()
        }
    }

    /**
     * Marks the given message as read. Also marks all messages below this id as read.
     * This will not affect the observable you get from [.unreadMessagesCount].
     */
    fun markAsRead(message: Api.Message) {
        markAsRead(message.unreadId, message.creationTime)
    }

    private fun messageIsUnread(notifyId: String, timestamp: Instant): Boolean {
        val upToTimestamp = readUpToCache.get(notifyId) ?: return true
        return timestamp.isAfter(upToTimestamp)
    }

    fun messageIsUnread(message: Api.Message): Boolean {
        return messageIsUnread(message.unreadId, message.creationTime)
    }

    /**
     * Forgets all read messages. This is useful on logout.
     */
    fun forgetUnreadMessages() {
        readUpToCache.evictAll()
        persistState()
    }

    /**
     * Returns an observable that produces the number of unread messages.
     */
    fun unreadMessagesCount(): Observable<Api.InboxCounts> {
        return unreadMessagesCount
    }

    fun publishUnreadMessagesCount(unreadCount: Api.InboxCounts) {
        unreadMessagesCount.onNext(unreadCount)
    }

    /**
     * Sends a private message to a receiver
     */
    suspend fun send(receiverId: Long, message: String) {
        api.sendMessageAsync(null, message, receiverId).await()
    }

    /**
     * Sends a private message to a receiver
     */
    suspend fun send(recipient: String, message: String): Api.ConversationMessages {
        val response = api.sendMessageAsync(null, message, recipient).await()
        if (response.error == "senderIsRecipient") {
            throw StringException(R.string.error_senderIsRecipient)
        }
        return response
    }

    suspend fun listConversations(olderThan: Instant? = null): Api.Conversations {
        return api.listConversationsAsync(olderThan?.epochSeconds).await()
    }

    suspend fun messagesInConversation(name: String, olderThan: Instant? = null): Api.ConversationMessages {
        val result = api.messagesWithAsync(name, olderThan?.epochSeconds).await()

        // mark the latest message in the conversation as read
        result.messages.maxBy { it.creationTime }?.let { markAsRead(name, it.creationTime) }

        return result
    }
}

val Api.Message.unreadId: String get() = if (isComment) "item:$itemId" else name
