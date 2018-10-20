package com.pr0gramm.app.services

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.util.logger
import rx.Completable
import rx.Observable
import rx.subjects.BehaviorSubject


/**
 * Service for receiving and sending private messages
 * as well as the combined inbox (comments and private messages)
 */
class InboxService(private val api: Api, private val preferences: SharedPreferences) {

    private val unreadMessagesCount = BehaviorSubject.create(0).toSerialized()

    /**
     * Gets the list of unread messages. You can not call this multiple times, because
     * it will mark the messages as read immediately.
     */
    val unreadMessages: Observable<List<Api.Message>> get() {
        publishUnreadMessagesCount(0)

        return api.inboxUnread()
                .map { it.messages }
                .doOnNext { messages ->
                    messages.maxBy { it.creationTime }?.let { markAsRead(it) }
                }
    }

    /**
     * Gets the list of inbox messages
     */
    val inbox: Observable<List<Api.Message>>
        get() = api.inboxAll().map { it.messages }

    /**
     * Gets the list of private messages.
     */
    val privateMessages: Observable<List<Api.PrivateMessage>>
        get() = api.inboxPrivateMessages().map { it.messages }

    /**
     * Gets all the comments a user has written
     */
    fun getUserComments(user: String, contentTypes: Set<ContentType>): Observable<Api.UserComments> {
        return api.userComments(user,
                (Instant.now() + Duration.days(1)).millis / 1000L,
                ContentType.combine(contentTypes))
    }

    private fun markAsRead(message: Api.Message) {
        markAsRead(message.creationTime.millis)
    }

    /**
     * Marks the given message as read. Also marks all messages below this id as read.
     * This will not affect the observable you get from [.unreadMessagesCount].
     */
    fun markAsRead(timestamp: Long) {
        val updateRequired = messageIsUnread(timestamp)
        if (updateRequired) {
            logger.info {
                "Mark all messages with timestamp less than or equal to $timestamp as read"
            }

            preferences.edit {
                putLong(KEY_MAX_READ_MESSAGE_ID, timestamp)
            }
        }
    }

    /**
     * Forgets all read messages. This is useful on logout.
     */
    fun forgetReadMessage() {
        preferences.edit {
            putLong(KEY_MAX_READ_MESSAGE_ID, 0)
        }
    }

    /**
     * Returns true if the given message was already read
     * according to [.markAsRead].
     */
    fun messageIsUnread(message: Api.Message): Boolean {
        return messageIsUnread(message.creationTime.millis)
    }

    fun messageIsUnread(timestamp: Long): Boolean {
        return timestamp > preferences.getLong(KEY_MAX_READ_MESSAGE_ID, 0)
    }

    /**
     * Returns an observable that produces the number of unread messages.
     */
    fun unreadMessagesCount(): Observable<Int> {
        return unreadMessagesCount.asObservable()
    }

    fun publishUnreadMessagesCount(unreadCount: Int) {
        unreadMessagesCount.onNext(unreadCount)
    }

    /**
     * Sends a private message to a receiver
     */
    fun send(receiverId: Long, message: String): Completable {
        return api.sendMessage(null, message, receiverId).toCompletable()
    }

    companion object {
        private val logger = logger("InboxService")
        private val KEY_MAX_READ_MESSAGE_ID = "InboxService.maxReadMessageId"
    }
}
