package com.pr0gramm.app.services;

import android.content.SharedPreferences;

import com.google.common.collect.Ordering;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.feed.ContentType;
import com.pr0gramm.app.feed.Nothing;

import org.joda.time.Duration;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

/**
 * Service for receiving and sending private messages
 * as well as the combined inbox (comments and private messages)
 */
@Singleton
public class InboxService {
    private static final Logger logger = LoggerFactory.getLogger("InboxService");
    private static final String KEY_MAX_READ_MESSAGE_ID = "InboxService.maxReadMessageId";

    private final Api api;
    private final SharedPreferences preferences;

    private final Subject<Integer, Integer> unreadMessagesCount = BehaviorSubject.create(0).toSerialized();

    @Inject
    public InboxService(Api api, SharedPreferences preferences) {
        this.api = api;
        this.preferences = preferences;
    }

    /**
     * Gets the list of unread messages. You can not call this multiple times, because
     * it will mark the messages as read immediately.
     */
    public Observable<List<Api.Message>> getUnreadMessages() {
        publishUnreadMessagesCount(0);
        return api.inboxUnread().map(Api.MessageFeed::getMessages).doOnNext(messages -> {
            if (messages.size() > 0) {
                markAsRead(Ordering.natural()
                        .onResultOf(Api.Message::creationTime)
                        .max(messages));
            }
        });
    }

    /**
     * Gets the list of inbox messages
     */
    public Observable<List<Api.Message>> getInbox() {
        return api.inboxAll().map(Api.MessageFeed::getMessages);
    }

    /**
     * Gets the list of private messages.
     */
    public Observable<List<Api.PrivateMessage>> getPrivateMessages() {
        return api.inboxPrivateMessages().map(Api.PrivateMessageFeed::getMessages);
    }

    /**
     * Gets all the comments a user has written
     */
    public Observable<Api.UserComments> getUserComments(String user, Set<ContentType> contentTypes) {
        return api.userComments(user,
                Instant.now().plus(Duration.standardDays(1)).getMillis() / 1000L,
                ContentType.combine(contentTypes));
    }

    /**
     * Marks the given message as read. Also marks all messages below this id as read.
     * This will not affect the observable you get from {@link #unreadMessagesCount()}.
     */
    public void markAsRead(Api.Message message) {
        markAsRead(message.creationTime().getMillis());
    }

    public void markAsRead(long timestamp) {
        boolean updateRequired = messageIsUnread(timestamp);
        if (updateRequired) {
            logger.info(
                    "Mark all messages with timestamp less than or equal to {} as read",
                    timestamp);

            preferences.edit()
                    .putLong(KEY_MAX_READ_MESSAGE_ID, timestamp)
                    .apply();
        }
    }

    /**
     * Forgets all read messages. This is useful on logout.
     */
    public void forgetReadMessage() {
        preferences.edit()
                .putLong(KEY_MAX_READ_MESSAGE_ID, 0)
                .apply();
    }

    /**
     * Returns true if the given message was already read
     * according to {@link #markAsRead(Api.Message)}.
     */
    public boolean messageIsUnread(Api.Message message) {
        return messageIsUnread(message.creationTime().getMillis());
    }

    public boolean messageIsUnread(long timestamp) {
        return timestamp > preferences.getLong(KEY_MAX_READ_MESSAGE_ID, 0);
    }

    /**
     * Returns an observable that produces the number of unread messages.
     */
    public Observable<Integer> unreadMessagesCount() {
        return unreadMessagesCount.asObservable();
    }

    void publishUnreadMessagesCount(int unreadCount) {
        unreadMessagesCount.onNext(unreadCount);
    }

    /**
     * Sends a private message to a receiver
     */
    public Observable<Nothing> send(long receiverId, String message) {
        return api.sendMessage(null, message, receiverId);
    }

}
