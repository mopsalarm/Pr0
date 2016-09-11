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
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.ui.InboxActivity;
import com.pr0gramm.app.ui.InboxType;
import com.pr0gramm.app.ui.UpdateActivity;
import com.pr0gramm.app.util.SenderDrawableProvider;
import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.functions.Actions;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Iterables.limit;
import static com.google.common.collect.Iterables.transform;
import static com.pr0gramm.app.services.ThemeHelper.primaryColor;

/**
 */
@Singleton
public class NotificationService {
    private static final Logger logger = LoggerFactory.getLogger("NotificationService");

    public static final int NOTIFICATION_NEW_MESSAGE_ID = 5001;
    public static final int NOTIFICATION_PRELOAD_ID = 5002;
    public static final int NOTIFICATION_UPDATE_ID = 5003;

    private final Settings settings;
    private final Application context;
    private final NotificationManagerCompat nm;
    private final InboxService inboxService;
    private final Picasso picasso;
    private final UriHelper uriHelper;
    private final UserService userService;
    private final BadgeService badgeService;

    @Inject
    public NotificationService(Application context, InboxService inboxService, Picasso picasso,
                               UserService userService, BadgeService badgeService) {

        this.context = context;
        this.inboxService = inboxService;
        this.picasso = picasso;
        this.userService = userService;
        this.uriHelper = UriHelper.of(context);
        this.settings = Settings.of(context);
        this.nm = NotificationManagerCompat.from(context);
        this.badgeService = badgeService;

        // update the icon to show the current inbox count.
        this.inboxService.unreadMessagesCount().subscribe(unreadCount ->
                badgeService.update(context, unreadCount));
    }

    public void showUpdateNotification(Update update) {
        Notification notification = new NotificationCompat.Builder(context)
                .setContentIntent(updateActivityIntent(update))
                .setContentTitle(context.getString(R.string.notification_update_available))
                .setContentText(context.getString(R.string.notification_update_available_text, update.versionStr()))
                .setSmallIcon(R.drawable.ic_notify_new_message)
                .addAction(R.drawable.ic_white_action_save, "Download", updateActivityIntent(update))
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .build();

        nm.notify(NOTIFICATION_UPDATE_ID, notification);
    }

    public void showForInbox(Api.Sync sync) {
        if (!settings.showNotifications())
            return;

        // try to get the new messages, ignore all errors.
        inboxService.getInbox()
                .map(messages -> FluentIterable.from(messages)
                        .limit(sync.inboxCount())
                        .filter(inboxService::messageIsUnread)
                        .toList())
                .toBlocking()
                .subscribe(messages -> showInboxNotification(sync, messages), Actions.empty());
    }

    private void showInboxNotification(Api.Sync sync, ImmutableList<Api.Message> messages) {
        if (messages.isEmpty() || !userService.isAuthorized()) {
            cancelForInbox();
            return;
        }

        String title = sync.inboxCount() == 1
                ? context.getString(R.string.notify_new_message_title)
                : context.getString(R.string.notify_new_messages_title, sync.inboxCount());

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        inboxStyle.setBigContentTitle(title);
        inboxStyle.setSummaryText(context.getString(R.string.notify_new_message_summary_text));
        for (Api.Message msg : limit(messages, 5)) {
            String sender = msg.getName();
            String message = msg.getMessage();

            //Create SpanableString to make styling possible
            SpannableString line = new SpannableString(sender + ' ' + message);
            line.setSpan(new StyleSpan(Typeface.BOLD), 0, sender.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

            //and add the line to our notification
            inboxStyle.addLine(line);
        }


        long timestamp = Ordering.natural().min(transform(messages, msg -> msg.getCreated().getMillis()));
        long maxMessageTimestamp = Ordering.natural().max(transform(messages, Api.Message::getCreated)).getMillis();

        Notification notification = new NotificationCompat.Builder(context)
                .setContentIntent(inboxActivityIntent(maxMessageTimestamp))
                .setContentTitle(title)
                .setContentText(context.getString(R.string.notify_new_message_summary_text))
                .setStyle(inboxStyle)
                .setSmallIcon(R.drawable.ic_notify_new_message)
                .setLargeIcon(thumbnail(messages).orNull())
                .setWhen(timestamp)
                .setShowWhen(timestamp != 0)
                .setAutoCancel(true)
                .setDeleteIntent(markAsReadIntent(maxMessageTimestamp))
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .setLights(ContextCompat.getColor(context, primaryColor()), 500, 500)
                .build();

        nm.notify(NOTIFICATION_NEW_MESSAGE_ID, notification);

        Track.notificationShown();
    }

    /**
     * Gets an optional "big" thumbnail for the given set of messages.
     */
    private Optional<Bitmap> thumbnail(List<Api.Message> messages) {
        Api.Message message = messages.get(0);

        boolean allForTheSamePost = FluentIterable.from(messages)
                .transform(Api.Message::getItemId)
                .toSet().size() == 1;

        if (allForTheSamePost && message.getItemId() != 0 && !isNullOrEmpty(message.thumbnail())) {
            return loadThumbnail(message);
        }

        boolean allForTheSameUser = FluentIterable.from(messages)
                .transform(Api.Message::getSenderId)
                .toSet().size() == 1;

        if (allForTheSameUser && message.getItemId() == 0 && !isNullOrEmpty(message.getName())) {
            int width = context.getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
            int height = context.getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_height);

            SenderDrawableProvider provider = new SenderDrawableProvider(context);
            return Optional.of(provider.makeSenderBitmap(message, width, height));
        }

        return Optional.absent();
    }

    private Optional<Bitmap> loadThumbnail(Api.Message message) {
        Uri uri = uriHelper.thumbnail(message);
        try {
            return Optional.of(picasso.load(uri).get());
        } catch (IOException ignored) {
            logger.warn("Could not load thumbnail for url: {}", uri);
            return Optional.absent();
        }
    }

    private PendingIntent inboxActivityIntent(long maxMessageTimestamp) {
        Intent intent = new Intent(context, InboxActivity.class);
        intent.putExtra(InboxActivity.EXTRA_INBOX_TYPE, InboxType.UNREAD.ordinal());
        intent.putExtra(InboxActivity.EXTRA_FROM_NOTIFICATION, true);
        intent.putExtra(InboxActivity.EXTRA_MESSAGE_TIMESTAMP, maxMessageTimestamp);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(intent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent markAsReadIntent(long maxMessageTimestamp) {
        Intent intent = new Intent(context, InboxNotificationCanceledReceiver.class);
        intent.putExtra(InboxNotificationCanceledReceiver.EXTRA_MESSAGE_TIMESTAMP, maxMessageTimestamp);

        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent updateActivityIntent(Update update) {
        Intent intent = new Intent(context, UpdateActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(UpdateActivity.EXTRA_UPDATE, update);

        return TaskStackBuilder.create(context)
                .addParentStack(UpdateActivity.class)
                .addNextIntent(intent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
    }


    public void cancelForInbox() {
        nm.cancel(NOTIFICATION_NEW_MESSAGE_ID);
    }

    public void cancelForUpdate() {
        nm.cancel(NOTIFICATION_UPDATE_ID);
    }
}
