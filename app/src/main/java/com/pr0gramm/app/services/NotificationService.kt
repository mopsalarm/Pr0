package com.pr0gramm.app.services

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.*
import androidx.core.content.FileProvider
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.ui.InboxActivity
import com.pr0gramm.app.ui.InboxType
import com.pr0gramm.app.ui.UpdateActivity
import com.pr0gramm.app.util.Logger
import com.pr0gramm.app.util.UserDrawables
import com.pr0gramm.app.util.getColorCompat
import com.pr0gramm.app.util.lruCache
import com.squareup.picasso.Picasso
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger


/**
 */

class NotificationService(private val context: Application,
                          private val inboxService: InboxService,
                          private val picasso: Picasso,
                          private val userService: UserService) {

    private val logger = Logger("NotificationService")

    private val settings: Settings = Settings.get()
    private val uriHelper: UriHelper = UriHelper.of(context)
    private val nm: NotificationManagerCompat = NotificationManagerCompat.from(context)

    private val downloadNotificationNextId = AtomicInteger(Types.Download.id)

    private val downloadNotificationIdCache = lruCache<File, Int>(128) {
        downloadNotificationNextId.incrementAndGet()
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannels()
        }

        // update the icon to show the current inbox count.
        this.inboxService.unreadMessagesCount().subscribe { unreadCount ->
            if (settings.showNotifications) {
                BadgeService().update(context, unreadCount.total)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannels() {
        createChannel(Types.NewMessage, NotificationManager.IMPORTANCE_LOW) {
            setShowBadge(true)
            enableLights(true)
            lightColor = context.getColorCompat(ThemeHelper.accentColor)
        }

        createChannel(Types.Update, NotificationManager.IMPORTANCE_LOW) {
            setShowBadge(false)
        }

        createChannel(Types.Download, NotificationManager.IMPORTANCE_LOW) {
            setShowBadge(false)
        }

        createChannel(Types.Preload, NotificationManager.IMPORTANCE_LOW) {
            setShowBadge(false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel(type: NotificationType, importance: Int, configure: NotificationChannel.() -> Unit = {}) {
        val ch = NotificationChannel(type.channel, context.getString(type.description), importance).apply(configure)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(ch)
    }

    private fun notify(type: NotificationType, id: Int? = null,
                       configure: NotificationCompat.Builder.() -> Unit) {

        val n = NotificationCompat.Builder(context, type.channel).apply(configure).build()
        nm.notify(id ?: type.id, n)
    }

    fun showUpdateNotification(update: Update) {
        notify(Types.Update) {
            setContentIntent(updateActivityIntent(update))
            setContentTitle(context.getString(R.string.notification_update_available))
            setContentText(context.getString(R.string.notification_update_available_text, update.versionStr()))
            setSmallIcon(R.drawable.ic_notify_new_message)
            addAction(R.drawable.ic_white_action_save, "Download", updateActivityIntent(update))
            setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            color = context.getColorCompat(ThemeHelper.accentColor)
            setAutoCancel(true)
        }
    }

    fun showDownloadNotification(file: File, progress: Float, preview: Bitmap? = null) {
        val id = downloadNotificationIdCache[file]
        notify(Types.Download, id) {
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

    suspend fun showUnreadMessagesNotification() {
        if (!settings.showNotifications)
            return

        // try to get the new messages, ignore all errors.
        runCatching {
            val messages = inboxService.pending()
            val unread = messages.filter { inboxService.messageIsUnread(it) }
            showInboxNotification(unread)
        }
    }

    @Suppress("UsePropertyAccessSyntax")
    private fun showInboxNotification(messages: List<Api.Message>) {
        if (messages.isEmpty() || !userService.isAuthorized) {
            cancelForInbox()
            return
        }

        val title = when {
            messages.size == 1 -> context.getString(R.string.notify_new_message_title)
            else -> context.getString(R.string.notify_new_messages_title, messages.size)
        }

        val inboxStyle = formatMessages(messages)

        val minMessageTimestamp = messages.minBy { it.creationTime }!!.creationTime
        val maxMessageTimestamp = messages.maxBy { it.creationTime }!!.creationTime

        val inboxIntent = when {
            messages.size == 1 && messages.first().isComment ->
                inboxActivityIntent(maxMessageTimestamp, InboxType.COMMENTS_IN)

            messages.size == 1 && !messages.first().isComment ->
                inboxActivityIntent(maxMessageTimestamp, InboxType.PRIVATE,
                        conversationName = messages.first().name)

            else ->
                inboxActivityIntent(maxMessageTimestamp, InboxType.PRIVATE)
        }

        notify(Types.NewMessage) {
            setContentIntent(inboxIntent)
            setContentTitle(title)
            setContentText(context.getString(R.string.notify_new_message_summary_text))
            setStyle(inboxStyle)
            setSmallIcon(R.drawable.ic_notify_new_message)
            setLargeIcon(thumbnail(messages))
            setWhen(minMessageTimestamp.millis)
            setShowWhen(minMessageTimestamp.millis != 0L)
            setAutoCancel(true)
            setDeleteIntent(markAsReadIntent(maxMessageTimestamp))
            setCategory(NotificationCompat.CATEGORY_EMAIL)

            setLights(context.getColorCompat(ThemeHelper.accentColor), 500, 500)
            setColor(context.getColorCompat(ThemeHelper.accentColor))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val replyToUserId = instantReplyToUserId(messages)
                if (replyToUserId != 0) {
                    val action = buildReplyAction(messages[0])
                    addAction(action)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            }
        }

        Track.inboxNotificationShown()
    }

    private fun formatMessages(messages: List<Api.Message>): NotificationCompat.MessagingStyle {
        val inboxStyle = NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
        messages.take(5).forEach { msg ->
            val p = Person.Builder().setName(msg.name).build()
            val m = NotificationCompat.MessagingStyle.Message(msg.message, msg.creationTime.millis, p)
            inboxStyle.addMessage(m)
        }

        return inboxStyle
    }

    fun showSendSuccessfulNotification(receiver: String) {
        notify(Types.NewMessage) {
            setContentIntent(inboxActivityIntent(Instant(0), InboxType.PRIVATE, receiver))
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
                .setLabel(context.getString(R.string.notify_reply_to_x, message.name))
                .build()

        // add everything as an action
        return NotificationCompat.Action.Builder(R.drawable.ic_reply, context.getString(R.string.notify_reply), pendingIntent)
                .addRemoteInput(remoteInput)
                .build()
    }

    /**
     * Gets an optional "big" thumbnail for the given set of messages.
     */
    private fun thumbnail(messages: List<Api.Message>): Bitmap? {
        val message = messages[0]

        val allForTheSamePost = messages.all { message.itemId == it.itemId }

        if (allForTheSamePost && message.itemId != 0L && !message.thumbnail.isNullOrEmpty()) {
            return loadThumbnail(message)
        }

        val allForTheSameUser = messages.all { message.senderId == it.senderId }

        if (allForTheSameUser && message.itemId == 0L && message.name.isNotEmpty()) {
            val width = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
            val height = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)

            val provider = UserDrawables(context)
            return provider.makeSenderBitmap(message, width, height)
        }

        return null
    }

    private fun loadThumbnail(message: Api.Message): Bitmap? {
        val uri = uriHelper.thumbnail(message)
        return try {
            picasso.load(uri).get()
        } catch (ignored: IOException) {
            logger.warn { "Could not load thumbnail for url: $uri" }
            null
        }
    }

    private fun inboxActivityIntent(timestamp: Instant, inboxType: InboxType, conversationName: String? = null): PendingIntent {
        val intent = Intent(context, InboxActivity::class.java)
        intent.putExtra(InboxActivity.EXTRA_INBOX_TYPE, inboxType.ordinal)
        intent.putExtra(InboxActivity.EXTRA_FROM_NOTIFICATION, true)
        intent.putExtra(InboxActivity.EXTRA_MESSAGE_TIMESTAMP, timestamp)

        if (conversationName != null) {
            intent.putExtra(InboxActivity.EXTRA_CONVERSATION_NAME, conversationName)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        return TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(intent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)!!
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
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)!!
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
        nm.cancel(Types.NewMessage.id)
    }

    fun cancelForUpdate() {
        nm.cancel(Types.Update.id)
    }

    /**
     * If all messages are from the same user, we return this user id as the one
     * that we can send a reply to.
     */
    private fun instantReplyToUserId(messages: List<Api.Message>): Int {
        val sender = messages[0].senderId
        return if (messages.all { it.senderId == sender }) sender else 0
    }

    class NotificationType(val id: Int, channelName: String, val description: Int) {
        val channel = "com.pr0gramm.app." + channelName
    }

    object Types {
        val NewMessage = NotificationType(5001, "NEW_MESSAGE", R.string.notification_channel_new_message)
        val Preload = NotificationType(5002, "PRELOAD", R.string.notification_channel_preload)
        val Update = NotificationType(5003, "UPDATE", R.string.notification_channel_update)
        val Download = NotificationType(6000, "DOWNLOAD", R.string.notification_channel_download)
    }

    fun beginPreloadNotification(): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, Types.Preload.channel)
    }
}
