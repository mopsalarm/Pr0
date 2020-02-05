package com.pr0gramm.app.services

import android.content.SharedPreferences
import androidx.collection.LruCache
import androidx.core.content.edit
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.*
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.model.inbox.UnreadMarkerTimestamp
import com.pr0gramm.app.util.StringException
import com.pr0gramm.app.util.getJSON
import com.pr0gramm.app.util.setObject
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

    init {
        // restore previous cache entries
        val readUpToTimestamps: List<UnreadMarkerTimestamp> = preferences
                .getJSON("InboxService.readUpToCache") ?: listOf()

        readUpToTimestamps.forEach {
            readUpToCache.put(it.id, it.timestamp)
        }
    }

    private fun persistState() {
        val values = readUpToCache.snapshot().map { entry -> UnreadMarkerTimestamp(entry.key, entry.value) }
        preferences.edit {
            setObject("InboxService.readUpToCache", values)
        }
    }

    /**
     * Gets unread messages
     */
    suspend fun pending(): List<Message> {
        return convertInbox(api.inboxPending()) {
            it.toMessage()
        }
    }

    /**
     * Gets unread messages
     */
    suspend fun fetchAll(olderThan: Instant?): List<Message> {
        return convertInbox(api.inboxAll(olderThan?.epochSeconds)) {
            it.toMessage()
        }
    }

    /**
     * Gets the list of inbox comments
     */
    suspend fun fetchComments(olderThan: Instant? = null): List<Message> {
        return convertInbox(api.inboxComments(olderThan?.epochSeconds)) {
            it.toCommentMessage()
        }
    }

    suspend fun fetchNotifications(olderThan: Instant? = null): List<Message> {
        return convertInbox(api.inboxNotifications(olderThan?.epochSeconds)) {
            it.toNotificationMessage()
        }
    }

    suspend fun fetchFollows(olderThan: Instant? = null): List<Message> {
        return convertInbox(api.inboxFollows(olderThan?.epochSeconds)) {
            it.toFollowsMessage()
        }
    }

    private fun convertInbox(inbox: Api.Inbox, convert: (Api.Inbox.Item) -> Message?): List<Message> {
        val converted = inbox.messages.mapNotNull(convert)

        // mark the most recent item/message as read.
        converted.maxBy { it.creationTime }?.let { markAsRead(it) }

        return converted
    }

    /**
     * Gets all the comments a user has written
     */
    suspend fun getUserComments(user: String, contentTypes: Set<ContentType>, olderThan: Instant? = null): Api.UserComments {
        val beforeInSeconds = (olderThan ?: Instant.now().plus(Duration.days(1))).epochSeconds
        return api.userComments(user, beforeInSeconds, ContentType.combine(contentTypes))
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
    private fun markAsRead(message: Message) {
        markAsRead(message.unreadId, message.creationTime)
    }

    private fun messageIsUnread(notifyId: String, timestamp: Instant): Boolean {
        val upToTimestamp = readUpToCache.get(notifyId) ?: return true
        return timestamp.isAfter(upToTimestamp)
    }

    fun messageIsUnread(message: Message): Boolean {
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
        api.sendMessage(null, message, receiverId)
    }

    /**
     * Sends a private message to a receiver
     */
    suspend fun send(recipient: String, message: String): Api.ConversationMessages {
        val response = api.sendMessage(null, message, recipient)
        if (response.error == "senderIsRecipient") {
            throw StringException("senderIsRecipient", R.string.error_senderIsRecipient)
        }
        return response
    }

    suspend fun listConversations(olderThan: Instant? = null): Api.Conversations {
        return api.listConversations(olderThan?.epochSeconds)
    }

    suspend fun messagesInConversation(name: String, olderThan: Instant? = null): Api.ConversationMessages {
        val result = api.messagesWith(name, olderThan?.epochSeconds)

        // mark the latest message in the conversation as read
        result.messages.maxBy { it.creationTime }?.let { markAsRead(name, it.creationTime) }

        return result
    }
}

val Message.unreadId: String get() = if (isComment) "item:$itemId" else "${type}:${name}"
