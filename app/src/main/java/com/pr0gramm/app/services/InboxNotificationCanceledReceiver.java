package com.pr0gramm.app.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.pr0gramm.app.Dagger;

import javax.inject.Inject;

/**
 */
public class InboxNotificationCanceledReceiver extends BroadcastReceiver {
    public static final String EXTRA_MESSAGE_ID = "InboxNotificationCanceledReceiver.messageId";

    @Inject
    InboxService inboxService;

    @Override
    public void onReceive(Context context, Intent intent) {
        Dagger.appComponent(context).inject(this);

        long messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, 0);
        if (messageId > 0) {
            inboxService.markAsRead(messageId);
        }
    }
}
