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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.app.TaskStackBuilder
import androidx.core.content.FileProvider
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.ui.InboxActivity
import com.pr0gramm.app.ui.InboxType
import com.pr0gramm.app.ui.UpdateActivity
import com.pr0gramm.app.util.*
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

    val nm: NotificationManagerCompat = NotificationManagerCompat.from(context)

    private val notificationNextId = AtomicInteger(Types.Download.id)

    private val notificationIdCache = lruCache<File, Int>(128) {
        notificationNextId.incrementAndGet()
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
        listOf(Types.NewMessage, Types.NewComment).forEach { type ->
            createChannel(type, NotificationManager.IMPORTANCE_LOW) {
                setShowBadge(true)
                enableLights(true)
                lightColor = context.getColorCompat(ThemeHelper.accentColor)
            }
        }

        listOf(Types.Update, Types.Download, Types.Preload).forEach { type ->
            createChannel(type, NotificationManager.IMPORTANCE_LOW) {
                setShowBadge(false)
            }
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
        val id = notificationIdCache[file]
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
        catchAll {
            val messages = inboxService.pending()
            val unread = messages.filter { inboxService.messageIsUnread(it) }
            showInboxNotification(unread)
        }
    }

    private inner class MessageGroup(private val messages: List<Api.Message>) {
        private val isComment = messages.all { it.isComment }

        private val maxTimestamp = messages.maxBy { it.creationTime }!!.creationTime

        val type = if (isComment) Types.NewComment else Types.NewMessage
        val id = type.id + if (isComment) messages.first().itemId.toInt() else messages.first().senderId

        val title: String = when {
            !isComment -> messages.first().name
            isComment && messages.size == 1 -> context.getString(R.string.notify_new_comment_title)
            isComment -> context.getString(R.string.notify_new_comments_title, messages.size)
            else -> "unreachable"
        }

        val contentText: String = context.getString(R.string.notify_new_message_summary_text)

        val inboxIntent: PendingIntent = run {
            when {
                isComment -> inboxActivityIntent(maxTimestamp, InboxType.COMMENTS_IN)
                else -> inboxActivityIntent(maxTimestamp, InboxType.PRIVATE, messages.first().name)
            }
        }

        val deleteIntent: PendingIntent = markAsReadIntent(maxTimestamp)

        val icon: Bitmap? by lazy { thumbnail(messages) }

        val timestampWhen: Instant = messages.minBy { it.creationTime }!!.creationTime

        val replyAction: NotificationCompat.Action? = run {
            if (isComment && messages.size == 1 || !isComment) {
                buildReplyAction(id, messages.first())
            } else {
                null
            }
        }

        val style: NotificationCompat.Style? = NotificationCompat.InboxStyle().also { style ->
            if (!isComment) {
                style.setBigContentTitle(context.getString(R.string.notify_new_message_title, messages.first().name))
            }

            messages.sortedBy { it.creationTime }.take(5).forEach { message ->
                val line = if (isComment) {
                    buildSpannedString {
                        bold { append(message.name).append(": ") }
                        append(message.message)
                    }
                } else {
                    message.message
                }

                style.addLine(line)
            }
        }
    }

    @Suppress("UsePropertyAccessSyntax")
    private fun showInboxNotification(messages: List<Api.Message>) {
        if (messages.isEmpty() || !userService.isAuthorized) {
            cancelForInbox()
            return
        }

        val notifications = messages
                .groupBy { if (it.isComment) it.itemId.toInt() else it.senderId }
                .values.map { MessageGroup(it) }


        notifications.forEach { not ->
            notify(not.type, not.id) {
                setContentTitle(not.title)
                setContentIntent(not.inboxIntent)
                setContentText(not.contentText)

                setSmallIcon(R.drawable.ic_notify_new_message)
                setLargeIcon(not.icon)

                setWhen(not.timestampWhen.millis)
                setShowWhen(true)

                setAutoCancel(true)
                setDeleteIntent(not.deleteIntent)
                setCategory(NotificationCompat.CATEGORY_EMAIL)

                setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)

                not.style?.let { setStyle(it) }

                not.replyAction?.let { addAction(it) }
            }
        }

        Track.inboxNotificationShown()
    }

    fun showSendSuccessfulNotification(receiver: String, notificationId: Int) {
        notify(Types.NewMessage, notificationId) {
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
    private fun buildReplyAction(notificationId: Int, message: Api.Message): NotificationCompat.Action {
        // build the intent to fire on reply
        val pendingIntent = PendingIntent.getBroadcast(context, 0,
                MessageReplyReceiver.makeIntent(context, notificationId, message),
                PendingIntent.FLAG_UPDATE_CURRENT)

        // the input field
        val remoteInput = RemoteInput.Builder("msg")
                .setLabel(context.getString(R.string.notify_reply_to_x, message.name))
                .build()

        // add everything as an action
        return NotificationCompat.Action.Builder(R.drawable.ic_reply, context.getString(R.string.notify_reply), pendingIntent)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .addRemoteInput(remoteInput)
                .build()
    }

    /**
     * Gets an optional "big" thumbnail for the given set of messages.
     */
    private fun thumbnail(messages: List<Api.Message>): Bitmap? {
        val message = messages[0]

        val allSameItem = messages.all { message.itemId == it.itemId }
        if (allSameItem && message.isComment && !message.thumbnail.isNullOrEmpty()) {
            return loadItemThumbnail(message)
        }

        val allSameSender = messages.all { message.senderId == it.senderId }
        if (allSameSender && !message.isComment && message.name.isNotEmpty()) {
            // build an icon for the user
            val width = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
            val height = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)

            val provider = UserDrawables(context)
            return provider.makeSenderBitmap(message, width, height)
        }

        return null
    }

    private fun loadItemThumbnail(message: Api.Message): Bitmap? {
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
        // not 100% accurate, but should work
        nm.cancelAll()
    }

    fun cancelForUpdate() {
        nm.cancel(Types.Update.id)
    }

    class NotificationType(val id: Int, channelName: String, val description: Int) {
        val channel = "com.pr0gramm.app.$channelName"
    }

    object Types {
        val Preload = NotificationType(5002, "PRELOAD", R.string.notification_channel_preload)
        val Update = NotificationType(5003, "UPDATE", R.string.notification_channel_update)
        val Download = NotificationType(6000, "DOWNLOAD", R.string.notification_channel_download)

        val NewMessage = NotificationType(7000, "NEW_MESSAGE", R.string.notification_channel_new_message)
        val NewComment = NotificationType(1024 * 1024 * 1024, "NEW_COMMENT", R.string.notification_channel_new_comment)
    }

    fun beginPreloadNotification(): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, Types.Preload.channel)
    }
}
