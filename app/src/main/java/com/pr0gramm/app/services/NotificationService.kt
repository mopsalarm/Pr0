package com.pr0gramm.app.services

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.app.RemoteInput
import android.support.v4.app.TaskStackBuilder
import android.support.v4.content.ContextCompat
import com.google.common.base.Optional
import com.google.common.base.Strings.isNullOrEmpty
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.ThemeHelper.accentColor
import com.pr0gramm.app.ui.InboxActivity
import com.pr0gramm.app.ui.InboxType
import com.pr0gramm.app.ui.UpdateActivity
import com.pr0gramm.app.util.SenderDrawableProvider
import com.pr0gramm.app.util.onErrorResumeEmpty
import com.squareup.picasso.Picasso
import org.joda.time.Instant
import org.slf4j.LoggerFactory
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 */
@Singleton
class NotificationService @Inject constructor(private val context: Application,
                                              private val inboxService: InboxService,
                                              private val picasso: Picasso,
                                              private val userService: UserService) {

    private val settings: Settings = Settings.get()
    private val uriHelper: UriHelper = UriHelper.of(context)
    private val nm: NotificationManagerCompat = NotificationManagerCompat.from(context)

    init {
        // update the icon to show the current inbox count.
        this.inboxService.unreadMessagesCount().subscribe {
            unreadCount ->
            BadgeService().update(context, unreadCount)
        }
    }

    fun showUpdateNotification(update: Update) {
        nm.notify(NOTIFICATION_UPDATE_ID, newNotificationBuilder(context).run {
            setContentIntent(updateActivityIntent(update))
            setContentTitle(context.getString(R.string.notification_update_available))
            setContentText(context.getString(R.string.notification_update_available_text, update.versionStr()))
            setSmallIcon(R.drawable.ic_notify_new_message)
            addAction(R.drawable.ic_white_action_save, "Download", updateActivityIntent(update))
            setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            setAutoCancel(true)
            build()
        })
    }

    fun showForInbox(sync: Api.Sync) {
        if (!settings.showNotifications)
            return

        // try to get the new messages, ignore all errors.
        inboxService.inbox.onErrorResumeEmpty().toBlocking().subscribe { messages ->
            val unread = messages.take(sync.inboxCount()).filter { inboxService.messageIsUnread(it) }
            showInboxNotification(sync, unread)
        }
    }

    private fun showInboxNotification(sync: Api.Sync, messages: List<Api.Message>) {
        if (messages.isEmpty() || !userService.isAuthorized) {
            cancelForInbox()
            return
        }

        val title = when {
            sync.inboxCount() == 1 -> context.getString(R.string.notify_new_message_title)
            else -> context.getString(R.string.notify_new_messages_title, sync.inboxCount())
        }

        val inboxStyle = formatMessages(messages)

        val minMessageTimestamp = messages.minBy { it.creationTime() }!!.creationTime()
        val maxMessageTimestamp = messages.maxBy { it.creationTime() }!!.creationTime()

        val builder = newNotificationBuilder(context).apply {
            setContentIntent(inboxActivityIntent(maxMessageTimestamp, InboxType.UNREAD))
            setContentTitle(title)
            setContentText(context.getString(R.string.notify_new_message_summary_text))
            setStyle(inboxStyle)
            setSmallIcon(R.drawable.ic_notify_new_message)
            setLargeIcon(thumbnail(messages).orNull())
            setWhen(minMessageTimestamp.millis)
            setShowWhen(minMessageTimestamp.millis != 0L)
            setAutoCancel(true)
            setDeleteIntent(markAsReadIntent(maxMessageTimestamp))
            setCategory(NotificationCompat.CATEGORY_EMAIL)
            setLights(ContextCompat.getColor(context, accentColor), 500, 500)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val replyToUserId = instantReplyToUserId(messages)
            if (replyToUserId != 0) {
                val action = buildReplyAction(messages[0])
                builder.addAction(action)
            }
        }

        nm.notify(NOTIFICATION_NEW_MESSAGE_ID, builder.build())
        Track.notificationShown()
    }

    private fun formatMessages(messages: List<Api.Message>): NotificationCompat.MessagingStyle {
        val inboxStyle = NotificationCompat.MessagingStyle("Me")
        messages.take(5).forEach { msg ->
            inboxStyle.addMessage(msg.message(), msg.creationTime().millis, msg.name())
        }

        return inboxStyle
    }

    fun showSendSuccessfulNotification(receiver: String) {
        val builder = newNotificationBuilder(context).apply {
            setContentIntent(inboxActivityIntent(Instant(0), InboxType.PRIVATE))
            setContentTitle(context.getString(R.string.notify_message_sent_to, receiver))
            setContentText(context.getString(R.string.notify_goto_inbox))
            setSmallIcon(R.drawable.ic_notify_new_message)
            setAutoCancel(true)
            setCategory(NotificationCompat.CATEGORY_EMAIL)
        }

        nm.notify(NOTIFICATION_NEW_MESSAGE_ID, builder.build())
    }

    /**
     * This builds the little "reply" action under a notification.
     */
    private fun buildReplyAction(message: Api.Message): NotificationCompat.Action {
        // build the intent to fire on reply
        val pendingIntent = PendingIntent.getBroadcast(context, 0,
                MessageReplyReceiver.makeIntent(context, message),
                PendingIntent.FLAG_UPDATE_CURRENT)

        // the input field
        val remoteInput = RemoteInput.Builder("msg")
                .setLabel(context.getString(R.string.notify_reply_to_x, message.name()))
                .build()

        // add everything as an action
        return NotificationCompat.Action.Builder(R.drawable.ic_reply, context.getString(R.string.notify_reply), pendingIntent)
                .addRemoteInput(remoteInput)
                .build()
    }

    /**
     * Gets an optional "big" thumbnail for the given set of messages.
     */
    private fun thumbnail(messages: List<Api.Message>): Optional<Bitmap> {
        val message = messages[0]

        val allForTheSamePost = messages.all { message.itemId() == it.itemId() }

        if (allForTheSamePost && message.itemId() != 0L && !isNullOrEmpty(message.thumbnail())) {
            return loadThumbnail(message)
        }

        val allForTheSameUser = messages.all { message.senderId() == it.senderId() }

        if (allForTheSameUser && message.itemId() == 0L && !isNullOrEmpty(message.name())) {
            val width = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
            val height = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)

            val provider = SenderDrawableProvider(context)
            return Optional.of(provider.makeSenderBitmap(message, width, height))
        }

        return Optional.absent<Bitmap>()
    }

    private fun loadThumbnail(message: Api.Message): Optional<Bitmap> {
        val uri = uriHelper.thumbnail(message)
        try {
            return Optional.of(picasso.load(uri).get())
        } catch (ignored: IOException) {
            logger.warn("Could not load thumbnail for url: {}", uri)
            return Optional.absent<Bitmap>()
        }

    }

    private fun inboxActivityIntent(timestamp: Instant, inboxType: InboxType): PendingIntent {
        val intent = Intent(context, InboxActivity::class.java)
        intent.putExtra(InboxActivity.EXTRA_INBOX_TYPE, inboxType.ordinal)
        intent.putExtra(InboxActivity.EXTRA_FROM_NOTIFICATION, true)
        intent.putExtra(InboxActivity.EXTRA_MESSAGE_TIMESTAMP, timestamp.millis)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        return TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(intent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun markAsReadIntent(timestamp: Instant): PendingIntent {
        val intent = InboxNotificationCanceledReceiver.makeIntent(context, timestamp)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun updateActivityIntent(update: Update): PendingIntent {
        val intent = Intent(context, UpdateActivity::class.java)
        intent.putExtra(UpdateActivity.EXTRA_UPDATE, update)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        return TaskStackBuilder.create(context)
                .addParentStack(UpdateActivity::class.java)
                .addNextIntent(intent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
    }


    fun cancelForInbox() {
        nm.cancel(NOTIFICATION_NEW_MESSAGE_ID)
    }

    fun cancelForUpdate() {
        nm.cancel(NOTIFICATION_UPDATE_ID)
    }

    /**
     * If all messages are from the same user, we return this user id as the one
     * that we can send a reply to.
     */
    private fun instantReplyToUserId(messages: List<Api.Message>): Int {
        val sender = messages[0].senderId()
        return if (messages.all { it.senderId() == sender }) sender else 0
    }

    /**
     * Creates a new v7 notification buidler
     */
    private fun newNotificationBuilder(context: Context): NotificationCompat.Builder {
        return android.support.v7.app.NotificationCompat.Builder(context)
    }

    companion object {
        private val logger = LoggerFactory.getLogger("NotificationService")

        val NOTIFICATION_NEW_MESSAGE_ID = 5001
        val NOTIFICATION_PRELOAD_ID = 5002
        val NOTIFICATION_UPDATE_ID = 5003
    }
}
