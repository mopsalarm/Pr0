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
import android.support.v4.content.FileProvider
import com.google.common.base.Optional
import com.google.common.base.Strings.isNullOrEmpty
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.ThemeHelper.accentColor
import com.pr0gramm.app.ui.InboxActivity
import com.pr0gramm.app.ui.InboxType
import com.pr0gramm.app.ui.UpdateActivity
import com.pr0gramm.app.util.SenderDrawableProvider
import com.pr0gramm.app.util.lruCache
import com.pr0gramm.app.util.onErrorResumeEmpty
import com.squareup.picasso.Picasso
import org.joda.time.Instant
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger


/**
 */

class NotificationService(private val context: Application,
                          private val inboxService: InboxService,
                          private val picasso: Picasso,
                          private val userService: UserService) {

    private val settings: Settings = Settings.get()
    private val uriHelper: UriHelper = UriHelper.of(context)
    private val nm: NotificationManagerCompat = NotificationManagerCompat.from(context)

    private val downloadNotificationNextId = AtomicInteger(NOTIFICATION_DOWNLOAD_BASE_ID)
    private val downloadNotificationIdCache = lruCache<File, Int>(128) { downloadNotificationNextId.incrementAndGet() }

    init {
        // update the icon to show the current inbox count.
        this.inboxService.unreadMessagesCount().subscribe { unreadCount ->
            BadgeService().update(context, unreadCount)
        }
    }

    private fun notify(id: Int, tag: String? = null, configure: NotificationCompat.Builder.() -> Unit) {
        val n = newNotificationBuilder(context).apply(configure).build()
        nm.notify(tag, id, n)
    }

    fun showUpdateNotification(update: Update) {
        notify(NOTIFICATION_UPDATE_ID) {
            setContentIntent(updateActivityIntent(update))
            setContentTitle(context.getString(R.string.notification_update_available))
            setContentText(context.getString(R.string.notification_update_available_text, update.versionStr()))
            setSmallIcon(R.drawable.ic_notify_new_message)
            addAction(R.drawable.ic_white_action_save, "Download", updateActivityIntent(update))
            setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            setAutoCancel(true)
        }
    }

    fun showDownloadNotification(file: File, progress: Float, preview: Bitmap? = null) {
        val id = downloadNotificationIdCache[file]
        notify(id) {
            setContentTitle(file.nameWithoutExtension)
            setSmallIcon(R.drawable.ic_notify_new_message)
            setCategory(NotificationCompat.CATEGORY_PROGRESS)

            if (preview != null) {
                setLargeIcon(preview)
            }

            if (progress < 1) {
                // only show progress if download is still in progress.
                setProgress(1000, (1000f * progress.coerceIn(0f, 1f)).toInt(), progress <= 0)
                setSmallIcon(android.R.drawable.stat_sys_download)
                setAutoCancel(false)
            } else {
                setContentText(context.getString(R.string.download_complete))
                setSmallIcon(android.R.drawable.stat_sys_download_done)

                // make it clickable
                setAutoCancel(true)
                setContentIntent(viewFileIntent(file))
            }
        }
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

        notify(NOTIFICATION_NEW_MESSAGE_ID) {
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val replyToUserId = instantReplyToUserId(messages)
                if (replyToUserId != 0) {
                    val action = buildReplyAction(messages[0])
                    addAction(action)
                }
            }
        }

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
        notify(NOTIFICATION_NEW_MESSAGE_ID) {
            setContentIntent(inboxActivityIntent(Instant(0), InboxType.PRIVATE))
            setContentTitle(context.getString(R.string.notify_message_sent_to, receiver))
            setContentText(context.getString(R.string.notify_goto_inbox))
            setSmallIcon(R.drawable.ic_notify_new_message)
            setAutoCancel(true)
            setCategory(NotificationCompat.CATEGORY_EMAIL)
        }
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

    private fun viewFileIntent(file: File): PendingIntent {
        val provider = BuildConfig.APPLICATION_ID + ".FileProvider"
        val uri = FileProvider.getUriForFile(context, provider, file)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, MimeTypeHelper.guessFromFileExtension(file))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
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

        const val NOTIFICATION_NEW_MESSAGE_ID = 5001
        const val NOTIFICATION_PRELOAD_ID = 5002
        const val NOTIFICATION_UPDATE_ID = 5003
        const val NOTIFICATION_DOWNLOAD_BASE_ID = 6000;
    }
}
