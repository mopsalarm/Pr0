package com.pr0gramm.app;

import android.app.Application;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.pr0gramm.app.api.pr0gramm.response.Sync;
import com.pr0gramm.app.ui.InboxActivity;
import com.pr0gramm.app.ui.InboxType;

/**
 */
@Singleton
public class NotificationService {
    public static final int NOTIFICATION_NEW_MESSAGE_ID = 5001;
    public static final int NOTIFICATION_PRELOAD_ID = 5002;

    private final Settings settings;
    private final Application context;
    private final NotificationManagerCompat nm;

    @Inject
    public NotificationService(Application context) {
        this.context = context;
        this.settings = Settings.of(context);
        this.nm = NotificationManagerCompat.from(context);
    }

    public void showForInbox(Sync sync) {
        if (!settings.showNotifications())
            return;

        String title = sync.getInboxCount() == 1
                ? context.getString(R.string.notify_new_message_title)
                : context.getString(R.string.notify_new_messages_title);

        String content = sync.getInboxCount() == 1
                ? context.getString(R.string.notify_new_message_text)
                : context.getString(R.string.notify_new_messages_text, sync.getInboxCount());

        Intent intent = new Intent(context, InboxActivity.class);
        intent.putExtra(InboxActivity.EXTRA_INBOX_TYPE, InboxType.UNREAD.ordinal());
        intent.putExtra(InboxActivity.EXTRA_FROM_NOTIFICATION, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(context)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_notify_new_message)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .setLights(context.getResources().getColor(R.color.primary), 500, 500)
                .build();

        nm.notify(NOTIFICATION_NEW_MESSAGE_ID, notification);

        Track.notificationShown();
    }

    public void cancelForInbox() {
        nm.cancel(NOTIFICATION_NEW_MESSAGE_ID);
    }
}
