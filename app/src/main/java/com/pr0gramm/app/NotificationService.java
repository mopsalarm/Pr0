package com.pr0gramm.app;

import android.app.Application;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.SpannableString;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.api.pr0gramm.response.MessageFeed;
import com.pr0gramm.app.api.pr0gramm.response.PrivateMessage;
import com.pr0gramm.app.api.pr0gramm.response.Sync;
import com.pr0gramm.app.services.InboxService;
import com.pr0gramm.app.ui.InboxActivity;
import com.pr0gramm.app.ui.InboxType;

import java.util.List;

import rx.functions.Action1;

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


    @javax.inject.Inject
    private InboxService inboxService;

    public void showForInbox(Sync sync) {
        if (!settings.showNotifications())
            return;

        String title = sync.getInboxCount() == 1
                ? context.getString(R.string.notify_new_message_title)
                : context.getString(R.string.notify_new_messages_title);

        //String content = sync.getInboxCount() == 1
        //        ? context.getString(R.string.notify_new_message_text)
        //        : context.getString(R.string.notify_new_messages_text, sync.getInboxCount());

        Intent intent = new Intent(context, InboxActivity.class);
        intent.putExtra(InboxActivity.EXTRA_INBOX_TYPE, InboxType.UNREAD.ordinal());
        intent.putExtra(InboxActivity.EXTRA_FROM_NOTIFICATION, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        inboxStyle.setBigContentTitle(title);

        inboxService.getUnreadMessages().subscribe(new Action1<List<Message>>() {
            @Override
            public void call(List<Message> messages) {
                for (Message msg : messages) {
                    String sender = msg.getName();
                    String message = msg.getMessage();
                    int itemId = msg.getItemId();

                    //Create SpanableString to make styling possible
                    SpannableString sString = new SpannableString(sender + ' ' + message);
                    //Check if we got a private message or an answer to some image
                    if (0 == itemId) {
                        //PrivateMessage
                        //Set <sender> textstyle to BOLD and change its color
                        sString.setSpan(new StyleSpan(Typeface.BOLD), 0, sender.length(), 0);
                        sString.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.primary)), 0, sender.length(), 0);
                    } else {
                        //Image Answer
                        //Set <sender> textstyle to UNDERLINE and change its color
                        sString.setSpan(new UnderlineSpan(), 0, sender.length(), 0);
                        sString.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.primary_dark)), 0, sender.length(), 0);
                    }

                    //Make sure inboxStyle did not got terminated yet
                    if (null == inboxStyle)
                        break;
                    //and add the line to our notification
                    inboxStyle.addLine(sString);
                }
            }
        });

        Notification notification = new NotificationCompat.Builder(context)
                //        .setContentTitle(title)
                //        .setContentText(content)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_notify_new_message)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .setLights(context.getResources().getColor(R.color.primary), 500, 500)
                .setStyle(inboxStyle)
                .build();

        nm.notify(NOTIFICATION_NEW_MESSAGE_ID, notification);

        Track.notificationShown();
    }

    public void cancelForInbox() {
        nm.cancel(NOTIFICATION_NEW_MESSAGE_ID);
    }
}
