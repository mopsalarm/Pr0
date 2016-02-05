package com.pr0gramm.app.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.pr0gramm.app.Dagger;

import javax.inject.Inject;

/**
 */
public class InboxNotificationCanceledReceiver extends BroadcastReceiver {
    public static final String EXTRA_MESSAGE_TIMESTAMP = "InboxNotificationCanceledReceiver.messageTimestamp";

    @Inject
    InboxService inboxService;

    @Override
    public void onReceive(Context context, Intent intent) {
        Dagger.appComponent(context).inject(this);

        long timestamp = intent.getLongExtra(EXTRA_MESSAGE_TIMESTAMP, 0);
        if (timestamp > 0) {
            inboxService.markAsRead(timestamp);
        }

        // track this action
        Track.notificationClosed("swiped");
    }
}
