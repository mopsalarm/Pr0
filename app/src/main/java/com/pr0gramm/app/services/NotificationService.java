package com.pr0gramm.app.services;

import android.app.Application;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.RemoteInput;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;

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

import org.joda.time.Instant;
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
import static com.pr0gramm.app.services.ThemeHelper.accentColor;

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

        // update the icon to show the current inbox count.
        this.inboxService.unreadMessagesCount().subscribe(unreadCount ->
                badgeService.update(context, unreadCount));
    }

    public void showUpdateNotification(Update update) {
        Notification notification = newNotificationBuilder(context)
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

        NotificationCompat.Style inboxStyle = formatMessages(messages);

        Instant minMessageTimestamp = Ordering.natural().min(transform(messages, Api.Message::creationTime));
        Instant maxMessageTimestamp = Ordering.natural().max(transform(messages, Api.Message::creationTime));

        NotificationCompat.Builder builder = newNotificationBuilder(context)
                .setContentIntent(inboxActivityIntent(maxMessageTimestamp, InboxType.UNREAD))
                .setContentTitle(title)
                .setContentText(context.getString(R.string.notify_new_message_summary_text))
                .setStyle(inboxStyle)
                .setSmallIcon(R.drawable.ic_notify_new_message)
                .setLargeIcon(thumbnail(messages).orNull())
                .setWhen(minMessageTimestamp.getMillis())
                .setShowWhen(minMessageTimestamp.getMillis() != 0)
                .setAutoCancel(true)
                .setDeleteIntent(markAsReadIntent(maxMessageTimestamp))
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .setLights(ContextCompat.getColor(context, accentColor()), 500, 500);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            int replyToUserId = replyToUserId(messages);
            if (replyToUserId != 0) {
                NotificationCompat.Action action = buildReplyAction(messages.get(0));
                builder.addAction(action);
            }
        }

        nm.notify(NOTIFICATION_NEW_MESSAGE_ID, builder.build());
        Track.notificationShown();
    }

    private NotificationCompat.MessagingStyle formatMessages(ImmutableList<Api.Message> messages) {
        NotificationCompat.MessagingStyle inboxStyle = new NotificationCompat.MessagingStyle("Me");
        for (Api.Message msg : limit(messages, 5)) {
            inboxStyle.addMessage(msg.message(), msg.creationTime().getMillis(), msg.name());
        }
        return inboxStyle;
    }

    public void showSendSuccessfulNotification(String receiver) {
        NotificationCompat.Builder builder = newNotificationBuilder(context)
                .setContentIntent(inboxActivityIntent(new Instant(0), InboxType.PRIVATE))
                .setContentTitle(context.getString(R.string.notify_message_sent_to, receiver))
                .setContentText(context.getString(R.string.notify_goto_inbox))
                .setSmallIcon(R.drawable.ic_notify_new_message)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_EMAIL);

        nm.notify(NOTIFICATION_NEW_MESSAGE_ID, builder.build());
    }

    /**
     * This builds the little "reply" action under a notification.
     */
    private NotificationCompat.Action buildReplyAction(Api.Message message) {
        // build the intent to fire on reply
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
                MessageReplyReceiver.Companion.makeIntent(context, message),
                PendingIntent.FLAG_UPDATE_CURRENT);

        // the input field
        RemoteInput remoteInput = new RemoteInput.Builder("msg")
                .setLabel(context.getString(R.string.notify_reply_to_x, message.name()))
                .build();

        // add everything as an action
        return new NotificationCompat.Action
                .Builder(R.drawable.ic_reply, context.getString(R.string.notify_reply), pendingIntent)
                .addRemoteInput(remoteInput)
                .build();
    }

    /**
     * Only show if all messages are from the same sender
     */
    private static int replyToUserId(List<Api.Message> messages) {
        int sender = messages.get(0).senderId();
        for (Api.Message message : messages) {
            if (message.senderId() != sender) {
                return 0;
            }
        }

        return sender;
    }

    /**
     * Gets an optional "big" thumbnail for the given set of messages.
     */
    private Optional<Bitmap> thumbnail(List<Api.Message> messages) {
        Api.Message message = messages.get(0);

        boolean allForTheSamePost = FluentIterable.from(messages)
                .transform(Api.Message::itemId)
                .toSet().size() == 1;

        if (allForTheSamePost && message.itemId() != 0 && !isNullOrEmpty(message.thumbnail())) {
            return loadThumbnail(message);
        }

        boolean allForTheSameUser = FluentIterable.from(messages)
                .transform(Api.Message::senderId)
                .toSet().size() == 1;

        if (allForTheSameUser && message.itemId() == 0 && !isNullOrEmpty(message.name())) {
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

    private PendingIntent inboxActivityIntent(Instant timestamp, InboxType inboxType) {
        Intent intent = new Intent(context, InboxActivity.class);
        intent.putExtra(InboxActivity.EXTRA_INBOX_TYPE, inboxType.ordinal());
        intent.putExtra(InboxActivity.EXTRA_FROM_NOTIFICATION, true);
        intent.putExtra(InboxActivity.EXTRA_MESSAGE_TIMESTAMP, timestamp.getMillis());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(intent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent markAsReadIntent(Instant timestamp) {
        Intent intent = InboxNotificationCanceledReceiver.Companion.makeIntent(context, timestamp);
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


    /**
     * Creates a new v7 notification buidler
     */
    private static NotificationCompat.Builder newNotificationBuilder(Context context) {
        return new android.support.v7.app.NotificationCompat.Builder(context);
    }
}
