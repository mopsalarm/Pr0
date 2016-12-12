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

import rx.Observable;
import rx.schedulers.Schedulers;

/**
 * Reply directly to a user
 */
public class MessageReplyReceiver extends BroadcastReceiver {
    private static final Logger logger = LoggerFactory.getLogger("MessageReplyReceiver");

    @Inject
    InboxService inboxService;

    @Inject
    NotificationService notificationService;

    @Override
    public void onReceive(Context context, Intent intent) {
        Dagger.appComponent(context).inject(this);

        int receiverId = intent.getIntExtra("receiverId", 0);
        String receiverName = intent.getStringExtra("receiverName");
        String text = getMessageText(intent);

        // validate parameters
        if (receiverId == 0 || Strings.isNullOrEmpty(text) || Strings.isNullOrEmpty(receiverName)) {
            logger.error("No receiver id or message.");
            return;
        }

        // timestamp the original message was sent
        Instant messageCreated = new Instant(intent.getLongExtra("messageCreated", -1));

        // send message now
        inboxService.send(receiverId, text)
                .subscribeOn(Schedulers.io())
                .onErrorResumeNext(Observable.empty())
                .doOnNext(val -> {
                    notificationService.showSendSuccessfulNotification(receiverName);
                    markMessageAsRead(context, messageCreated);
                })
                .subscribe();
    }

    private void markMessageAsRead(Context context, Instant messageTimestamp) {
        Intent intent = InboxNotificationCanceledReceiver.makeIntent(context, messageTimestamp);
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
        intent.putExtra("receiverId", message.getSenderId());
        intent.putExtra("receiverName", message.getName());
        intent.putExtra("messageCreated", message.getCreated().getMillis());
        return intent;
    }
}
