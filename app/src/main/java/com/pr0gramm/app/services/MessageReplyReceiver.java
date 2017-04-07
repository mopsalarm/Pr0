package com.pr0gramm.app.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.RemoteInput;

import com.google.common.base.Strings;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.api.pr0gramm.Api;

import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import rx.Completable;
import rx.schedulers.Schedulers;

/**
 * Reply directly to a user
 */
public class MessageReplyReceiver extends BroadcastReceiver {
    private static final Logger logger = LoggerFactory.getLogger("MessageReplyReceiver");

    @Inject
    InboxService inboxService;

    @Inject
    VoteService voteService;

    @Inject
    NotificationService notificationService;

    @Override
    public void onReceive(Context context, Intent intent) {
        Dagger.appComponent(context).inject(this);

        // normal receiver info
        int receiverId = intent.getIntExtra("receiverId", 0);
        String receiverName = intent.getStringExtra("receiverName");

        // receiver infos for comments
        long itemId = intent.getLongExtra("itemId", 0);
        long commentId = intent.getLongExtra("commentId", 0);

        String text = getMessageText(intent);

        // validate parameters
        if (Strings.isNullOrEmpty(text) || Strings.isNullOrEmpty(receiverName)) {
            logger.error("No receiver id or message.");
            return;
        }

        // timestamp the original message was sent
        Instant messageCreated = new Instant(intent.getLongExtra("messageCreated", -1));

        // decide if we are sending a message or a comment
        boolean isMessage = itemId == 0 || commentId == 0;

        Completable result = isMessage
                ? sendResponseToMessage(receiverId, text)
                : sendResponseAsComment(itemId, commentId, text);

        // and handle the result.
        result.subscribeOn(Schedulers.io())
                .onErrorComplete()
                .subscribe(() -> {
                    notificationService.showSendSuccessfulNotification(receiverName);
                    markMessageAsRead(context, messageCreated);
                });
    }

    public Completable sendResponseAsComment(long itemId, long commentId, String text) {
        return voteService.postComment(itemId, commentId, text).toCompletable();
    }

    public Completable sendResponseToMessage(int receiverId, String text) {
        return inboxService.send(receiverId, text);
    }

    private void markMessageAsRead(Context context, Instant messageTimestamp) {
        Intent intent = InboxNotificationCanceledReceiver.Companion.makeIntent(context, messageTimestamp);
        context.sendBroadcast(intent);
    }

    @Nullable
    private static String getMessageText(Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            CharSequence msg = remoteInput.getCharSequence("msg");
            return msg != null ? msg.toString() : null;
        }

        return null;
    }

    public static Intent makeIntent(Context context, Api.Message message) {
        Intent intent = new Intent(context, MessageReplyReceiver.class);
        if (message.isComment()) {
            intent.putExtra("itemId", message.itemId());
            intent.putExtra("commentId", message.commentId());
        }

        intent.putExtra("receiverId", message.senderId());
        intent.putExtra("receiverName", message.name());

        intent.putExtra("messageCreated", message.creationTime().getMillis());
        return intent;
    }
}
