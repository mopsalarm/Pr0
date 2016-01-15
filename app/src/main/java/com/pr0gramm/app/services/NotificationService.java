package com.pr0gramm.app.services;

import android.app.Application;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.api.pr0gramm.response.Sync;
import com.pr0gramm.app.ui.InboxActivity;
import com.pr0gramm.app.ui.InboxType;
import com.pr0gramm.app.ui.UpdateActivity;
import com.squareup.picasso.Picasso;

import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Iterables.limit;
import static com.pr0gramm.app.services.ThemeHelper.primaryColor;

/**
 */
@Singleton
public class NotificationService {
    private static final Logger logger = LoggerFactory.getLogger("NotificationService");

    public static final int NOTIFICATION_NEW_MESSAGE_ID = 5001;
    public static final int NOTIFICATION_PRELOAD_ID = 5002;
    public static final int NOTIFICATION_UPDATE_ID = 5003;

    public static final int NOTIFICATION_IMPORTANT_MESSAGES_OFFSET = 6000;

    private final Settings settings;
    private final Application context;
    private final NotificationManagerCompat nm;
    private final InboxService inboxService;
    private final Picasso picasso;
    private final UriHelper uriHelper;

    @Inject
    public NotificationService(Application context, InboxService inboxService, Picasso picasso) {
        this.context = context;
        this.inboxService = inboxService;
        this.picasso = picasso;
        this.uriHelper = UriHelper.of(context);
        this.settings = Settings.of(context);
        this.nm = NotificationManagerCompat.from(context);
    }

    public void showUpdateNotification(Update update) {
        Notification notification = new NotificationCompat.Builder(context)
                .setContentIntent(newUpdateActivityIntent(update))
                .setContentTitle(context.getString(R.string.notification_update_available))
                .setContentText(context.getString(R.string.notification_update_available_text, update.version() % 100))
                .setSmallIcon(R.drawable.ic_notify_new_message)
                .addAction(R.drawable.ic_white_action_save, "Download", newDownloadUpdateIntent(update))
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .build();

        nm.notify(NOTIFICATION_UPDATE_ID, notification);
    }

    public void showForInbox(Sync sync) {
        if (!settings.showNotifications())
            return;

        String title = sync.getInboxCount() == 1
                ? context.getString(R.string.notify_new_message_title)
                : context.getString(R.string.notify_new_messages_title, sync.getInboxCount());

        // try to get the new messages, ignore all errors.
        ImmutableList<Message> newMessages = inboxService.getInbox()
                .map(messages -> limit(messages, sync.getInboxCount()))
                .map(ImmutableList::copyOf)
                .onErrorResumeNext(Observable.empty())
                .toBlocking()
                .firstOrDefault(ImmutableList.of());

        String summaryText = context.getString(R.string.notify_new_message_summary_text);

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        inboxStyle.setBigContentTitle(title);
        inboxStyle.setSummaryText(summaryText);
        for (Message msg : limit(newMessages, 5)) {
            String sender = msg.getName();
            String message = msg.getMessage();

            //Create SpanableString to make styling possible
            SpannableString line = new SpannableString(sender + ' ' + message);
            line.setSpan(new StyleSpan(Typeface.BOLD), 0, sender.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

            //and add the line to our notification
            inboxStyle.addLine(line);
        }

        Optional<Bitmap> thumbnail = Optional.absent();
        boolean allForTheSamePost = FluentIterable.from(newMessages)
                .transform(Message::getItemId)
                .toSet().size() == 1;

        if (allForTheSamePost) {
            Message message = newMessages.get(0);
            if (message.getItemId() != 0 && !isNullOrEmpty(message.getThumb())) {
                thumbnail = loadThumbnail(message);
            }
        }

        long timestamp = newMessages.isEmpty() ? 0 : Ordering.natural()
                .min(FluentIterable.from(newMessages)
                        .transform(Message::getCreated)
                        .transform(Instant::getMillis)
                        .toSortedList(Ordering.natural()));

        Notification notification = new NotificationCompat.Builder(context)
                .setContentIntent(newInboxActivityIntent())
                .setContentTitle(title)
                .setContentText(summaryText)
                .setStyle(inboxStyle)
                .setSmallIcon(R.drawable.ic_notify_new_message)
                .setLargeIcon(thumbnail.orNull())
                .setWhen(timestamp)
                .setShowWhen(timestamp != 0)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .setLights(ContextCompat.getColor(context, primaryColor()), 500, 500)
                .build();

        nm.notify(NOTIFICATION_NEW_MESSAGE_ID, notification);

        Track.notificationShown();
    }

    private Optional<Bitmap> loadThumbnail(Message message) {
        Uri uri = uriHelper.thumbnail(message);
        try {
            return Optional.of(picasso.load(uri).get());
        } catch (IOException ignored) {
            logger.warn("Could not load thumbnail for url: {}", uri);
            return Optional.absent();
        }
    }


    public void showImportantMessage(int messageId, Notification notification) {
        int id = NOTIFICATION_IMPORTANT_MESSAGES_OFFSET + messageId;
        nm.notify(id, notification);
    }

    private PendingIntent newInboxActivityIntent() {
        Intent intent = new Intent(context, InboxActivity.class);
        intent.putExtra(InboxActivity.EXTRA_INBOX_TYPE, InboxType.UNREAD.ordinal());
        intent.putExtra(InboxActivity.EXTRA_FROM_NOTIFICATION, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(intent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent newUpdateActivityIntent(Update update) {
        Intent intent = new Intent(context, UpdateActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(UpdateActivity.EXTRA_UPDATE, update);

        return TaskStackBuilder.create(context)
                .addParentStack(UpdateActivity.class)
                .addNextIntent(intent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent newDownloadUpdateIntent(Update update) {
        Intent intent = new Intent(context, DownloadUpdateReceiver.class);
        intent.putExtra(DownloadUpdateReceiver.EXTRA_UPDATE, update);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public void cancelForInbox() {
        nm.cancel(NOTIFICATION_NEW_MESSAGE_ID);
    }

    public void cancelForUpdate() {
        nm.cancel(NOTIFICATION_UPDATE_ID);
    }
}
