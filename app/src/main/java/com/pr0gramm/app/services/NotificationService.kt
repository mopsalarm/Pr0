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
import androidx.core.content.getSystemService
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
import java.util.Collections.synchronizedSet
import java.util.concurrent.atomic.AtomicInteger


/**
 */

class NotificationService(private val context: Application,
                          private val inboxService: InboxService,
                          private val picasso: Picasso) {

    private val logger = Logger("NotificationService")

    private val settings: Settings = Settings.get()
    private val uriHelper: UriHelper = UriHelper.of(context)

    private val nm: NotificationManagerCompat = NotificationManagerCompat.from(context)
    private val nmSystem: NotificationManager = context.getSystemService()!!

    private val notificationNextId = AtomicInteger(10000)

    private val notificationIdCache = lruCache<String, Int>(512) {
        notificationNextId.incrementAndGet()
    }

    private val inboxNotificationCache = synchronizedSet(mutableSetOf<NotificationId>())

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
        nm.notify(type.channel, id ?: type.id, n)
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
        val id = notificationIdCache["download:$file"]

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
            showInboxNotification(inboxService.pending())
        }
    }

    private val Api.Message.isUnread: Boolean get() = inboxService.messageIsUnread(this)

    @Suppress("UsePropertyAccessSyntax")
    private fun showInboxNotification(messages: List<Api.Message>) {
        if (!messages.any { it.isUnread }) {
            logger.debug { "No unread messages, cancel all existing notifications." }
            cancelForAllUnread()
            return
        }

        val messageGroups = messages
                .groupBy { if (it.isComment) it.itemId.toInt() else it.senderId }
                .values

        val messageGroupsWithUnread = messageGroups
                .filter { messageGroup -> messageGroup.any { it.isUnread } }

        // convert to notifications
        val notificationConfigs = messageGroupsWithUnread.map { MessageNotificationConfig(it) }

        // cache notification ids by sender name
        messages.forEach { message ->
            if (!message.isComment) {
                notificationIdCache.put("senderId:${message.name}", message.senderId)
            }
        }

        notificationConfigs.forEach { notifyConfig ->
            notify(notifyConfig.type, notifyConfig.id) {
                setContentTitle(notifyConfig.title)
                setContentIntent(notifyConfig.contentIntent)
                setContentText(notifyConfig.contentText)

                setColor(context.getColorCompat(ThemeHelper.accentColor))

                setSmallIcon(R.drawable.ic_notify_new_message)
                setLargeIcon(notifyConfig.icon)

                setWhen(notifyConfig.timestampWhen.millis)
                setShowWhen(true)

                setAutoCancel(true)
                setDeleteIntent(notifyConfig.deleteIntent)
                setCategory(NotificationCompat.CATEGORY_EMAIL)

                setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                setContentInfo("XXX")

                setStyle(notifyConfig.style)

                notifyConfig.replyAction?.let { addAction(it) }
            }

            inboxNotificationCache.add(NotificationId(notifyConfig.type.channel, notifyConfig.id))
        }

        Track.inboxNotificationShown()
    }

    fun showSendSuccessfulNotification(receiver: String, isMessage: Boolean, notificationId: Int) {
        val type = if (isMessage) Types.NewMessage else Types.NewComment
        notify(type, notificationId) {
            setContentIntent(inboxActivityIntent(InboxType.PRIVATE, receiver))
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
    private fun messageThumbnail(messages: List<Api.Message>): Bitmap? {
        val message = messages.first()

        if (message.isComment) {
            if (message.thumbnail?.isNotBlank() == true) {
                return loadItemThumbnail(message)
            }

        } else {
            val allSameSender = messages.all { message.senderId == it.senderId }
            if (allSameSender && message.name.isNotEmpty()) {
                // build an icon for the user
                val width = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
                val height = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)

                val provider = UserDrawables(context)
                return provider.makeSenderBitmap(message, width, height)
            }
        }

        // no thumbnail :/
        return null
    }

    private fun loadItemThumbnail(message: Api.Message): Bitmap? {
        val uri = uriHelper.thumbnail(message)
        return try {
            picasso.load(uri).get()
        } catch (ignored: IOException) {
            logger.warn { "Could not load thumbnail at url: $uri" }
            null
        }
    }

    private fun inboxActivityIntent(inboxType: InboxType, conversationName: String? = null): PendingIntent {
        val intent = Intent(context, InboxActivity::class.java)
        intent.putExtra(InboxActivity.EXTRA_INBOX_TYPE, inboxType.ordinal)
        intent.putExtra(InboxActivity.EXTRA_FROM_NOTIFICATION, true)

        if (conversationName != null) {
            intent.putExtra(InboxActivity.EXTRA_CONVERSATION_NAME, conversationName)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        return TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(intent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)!!
    }

    private fun markAsReadIntent(message: Api.Message): PendingIntent {
        val intent = InboxNotificationCanceledReceiver.makeIntent(context, message)
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

    fun cancelForUnreadConversation(conversationName: String) {
        val senderId = notificationIdCache.get("senderId:$conversationName") ?: return
        nm.cancel(Types.NewMessage.channel, senderId)
    }

    fun cancelForUnreadComments() {
        cancelAllByType(Types.NewComment)
    }

    fun cancelForAllUnread() {
        cancelAllByType(Types.NewComment)
        cancelAllByType(Types.NewMessage)
    }

    private fun cancelAllByType(type: NotificationType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            nmSystem.activeNotifications.forEach { notification ->
                if (notification.tag == type.channel) {
                    nm.cancel(notification.tag, notification.id)
                }
            }

        } else {
            inboxNotificationCache.forEach {
                if (it.tag == type.channel) {
                    nm.cancel(it.tag, it.id)
                }
            }
        }

        // now remove those from the cache
        inboxNotificationCache.removeAll { it.tag == type.channel }
    }

    fun cancelForUpdate() {
        nm.cancel(Types.Update.id)
    }


    private inner class MessageNotificationConfig(private val messages: List<Api.Message>) {
        private val message = messages.filter { it.isUnread }.maxBy { it.creationTime }
                ?: throw IllegalArgumentException("Must contain at least one unread message.")

        private val isComment = messages.all { it.isComment }

        val id = if (isComment) message.itemId.toInt() else message.senderId
        val type = if (isComment) Types.NewComment else Types.NewMessage

        val title: String = when {
            !isComment && messages.size > 1 -> {
                val more = context.getString(R.string.notification_hint_unread, messages.size)
                "${message.name} ($more)"
            }

            !isComment ->
                message.name

            isComment && messages.size == 1 ->
                context.getString(R.string.notify_new_comment_title)

            isComment ->
                context.getString(R.string.notify_new_comments_title, messages.size)

            else -> "unreachable"
        }

        val contentText: CharSequence = buildSpannedString {
            val text = if (message.message.length > 120) message.message.take(119) + "…" else message.message

            if (isComment) {
                bold { append(message.name).append(": ") }
                append(message.message)
            } else {
                append(text)
            }
        }

        val contentIntent: PendingIntent = when {
            isComment -> inboxActivityIntent(InboxType.COMMENTS_IN)
            else -> inboxActivityIntent(InboxType.PRIVATE, message.name)
        }

        val deleteIntent: PendingIntent = markAsReadIntent(message)

        val icon: Bitmap? = messageThumbnail(messages)

        val timestampWhen: Instant = messages.minBy { it.creationTime }!!.creationTime

        val replyAction: NotificationCompat.Action? = when {
            !isComment || messages.size == 1 -> buildReplyAction(id, message)
            else -> null
        }

        val style: NotificationCompat.Style = run {
            val me = Person.Builder()
                    .setName("Me")
                    .build()

            NotificationCompat.MessagingStyle(me).also { style ->
                messages.sortedBy { it.creationTime }.takeLast(5).forEach { message ->
                    val line = if (isComment) {
                        buildSpannedString {
                            bold { append(message.name).append(": ") }
                            append(message.message)
                        }
                    } else {
                        message.message
                    }

                    val styleMessage = NotificationCompat.MessagingStyle.Message(
                            message.message, message.creationTime.millis,
                            Person.Builder().setName(message.name).build())

                    style.addMessage(styleMessage)
                }

                if (isComment) {
                    style.isGroupConversation = true

                    style.conversationTitle = if (messages.size == 1) {
                        context.getString(R.string.notify_new_comment_title)
                    } else {
                        context.getString(R.string.notify_new_comments_title, messages.size)
                    }
                }

//                if (messages.size > 1) {
//                    style.setSummaryText(context.getString(
//                            R.string.notification_hint_unread, messages.size))
//                }
//
//                style.setBigContentTitle(when {
//                    !isComment -> message.name
//                    isComment && messages.size == 1 -> context.getString(R.string.notify_new_comment_title)
//                    isComment -> context.getString(R.string.notify_new_comments_title, messages.size)
//                    else -> "unreachable"
//                })
            }
        }
    }

    class NotificationType(val id: Int, channelName: String, val description: Int) {
        val channel = "com.pr0gramm.app.$channelName"
    }

    object Types {
        val Update = NotificationType(1, "UPDATE", R.string.notification_channel_update)
        val Preload = NotificationType(1, "PRELOAD", R.string.notification_channel_preload)
        val Download = NotificationType(1, "DOWNLOAD", R.string.notification_channel_download)

        val NewMessage = NotificationType(7000, "NEW_MESSAGE", R.string.notification_channel_new_message)
        val NewComment = NotificationType(7000, "NEW_COMMENT", R.string.notification_channel_new_comment)
    }

    private data class NotificationId(val tag: String, val id: Int)
}
