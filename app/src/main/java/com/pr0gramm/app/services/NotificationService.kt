package com.pr0gramm.app.services

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.app.TaskStackBuilder
import androidx.core.content.getSystemService
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import com.pr0gramm.app.Instant
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.api.pr0gramm.MessageType
import com.pr0gramm.app.api.pr0gramm.asThumbnail
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.feed.FeedService
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.feed.isVideoUri
import com.pr0gramm.app.parcel.Freezer
import com.pr0gramm.app.ui.*
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.injector
import com.squareup.picasso.Picasso
import java.io.IOException
import java.util.Collections.synchronizedSet
import java.util.concurrent.atomic.AtomicInteger


/**
 */

class NotificationService(private val context: Application,
                          private val inboxService: InboxService,
                          private val feedService: FeedService,
                          private val picasso: Picasso) {

    private val logger = Logger("NotificationService")

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
            BadgeService().update(context, unreadCount.total)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannels() {
        listOf(Types.NewMessage, Types.NewComment, Types.NewStalk, Types.NewNotification).forEach { type ->
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
            val icon = R.drawable.ic_white_action_save

            setContentIntent(updateActivityIntent(update))
            setContentTitle(context.getString(R.string.notification_update_available))
            setContentText(context.getString(R.string.notification_update_available_text, update.versionStr))
            setSmallIcon(R.drawable.ic_notify_new_message)
            addAction(icon, "Download", updateActivityIntent(update))
            setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            color = context.getColorCompat(ThemeHelper.accentColor)
            setAutoCancel(true)
        }
    }

    fun showDownloadNotification(uri: Uri, progress: Float, preview: Bitmap? = null) {
        val id = notificationIdCache["download:$uri"]

        notify(Types.Download, id) {
            setContentTitle(Storage.toFile(uri).nameWithoutExtension)
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
                setContentIntent(viewFileIntent(uri))
            }
        }
    }

    suspend fun showUnreadMessagesNotification() = catchAll {
        showInboxNotification(inboxService.pending())
    }

    private val Message.isUnread: Boolean get() = inboxService.messageIsUnread(this)

    @Suppress("UsePropertyAccessSyntax")
    private fun showInboxNotification(messages: List<Message>) {
        if (!messages.any { it.isUnread }) {
            logger.debug { "No unread messages, cancel all existing notifications." }
            cancelForAllUnread()
            return
        }

        val messageGroups = messages
                .filter { it.isUnread }
                .groupBy { Pair(it.type, it.groupId) }
                .values

        // convert to notifications
        val notificationConfigs = messageGroups.map { messages ->
            MessageNotificationConfig(context, messages)
        }

        // cache notification ids by sender name
        messages.forEach { message ->
            if (!message.isComment) {
                notificationIdCache.put("sender:${message.name}", message.senderId)
            }
        }

        notificationConfigs.forEach { notifyConfig ->
            notify(notifyConfig.type, notifyConfig.notificationId) {
                setContentTitle(notifyConfig.title)
                setContentText(notifyConfig.contentText)
                setContentIntent(notifyConfig.contentIntent)

                setColor(context.getColorCompat(ThemeHelper.accentColor))

                setSmallIcon(R.drawable.ic_notify_new_message)
                setLargeIcon(notifyConfig.icon)

                setWhen(notifyConfig.timestampWhen.millis)
                setShowWhen(true)

                setAutoCancel(true)
                setDeleteIntent(notifyConfig.deleteIntent)
                setCategory(NotificationCompat.CATEGORY_EMAIL)

                setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)

                setStyle(notifyConfig.style)

                notifyConfig.action?.let { action ->
                    addAction(action)
                }
            }

            inboxNotificationCache.add(NotificationId(notifyConfig.type.channel, notifyConfig.notificationId))
        }

        Track.inboxNotificationShown()
    }

    fun showSendSuccessfulNotification(receiver: String, isMessage: Boolean, notificationId: Int) {
        val type = if (isMessage) Types.NewMessage else Types.NewComment
        val contentIntent = NotificationHelperService(context).inboxActivityIntent(InboxType.PRIVATE, receiver)
        notify(type, notificationId) {
            setContentIntent(contentIntent)
            setContentTitle(context.getString(R.string.notify_message_sent_to, receiver))
            setContentText(context.getString(R.string.notify_goto_inbox))
            setSmallIcon(R.drawable.ic_notify_new_message)
            setAutoCancel(true)
            setCategory(NotificationCompat.CATEGORY_EMAIL)
        }
    }

    private fun updateActivityIntent(update: Update): PendingIntent {
        val intent = Intent(context, UpdateActivity::class.java)
        intent.putExtra(UpdateActivity.EXTRA_UPDATE, Freezer.freeze(update))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        return TaskStackBuilder.create(context)
                .addParentStack(UpdateActivity::class.java)
                .addNextIntent(intent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)!!
    }

    private fun viewFileIntent(uri: Uri): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, MimeTypeHelper.guessFromFileExtension(uri.toString()))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun cancelForUnreadConversation(conversationName: String) {
        val senderId = notificationIdCache.get("sender:$conversationName") ?: return
        nm.cancel(Types.NewMessage.channel, senderId)
    }

    fun cancelForUnreadComments() {
        cancelAllByType(Types.NewComment)
    }

    fun cancelForAllUnread() {
        cancelAllByType(Types.NewComment)
        cancelAllByType(Types.NewMessage)
    }

    private fun cancelAllByType(type: NotificationType) = catchAll {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ignoreAllExceptions {
                nmSystem.activeNotifications.forEach { notification ->
                    if (notification.tag == type.channel) {
                        nm.cancel(notification.tag, notification.id)
                    }
                }
            }

        } else {
            inboxNotificationCache.forEach {
                if (it.tag == type.channel) {
                    ignoreAllExceptions {
                        nm.cancel(it.tag, it.id)
                    }
                }
            }
        }

        // now remove those from the cache
        inboxNotificationCache.removeAll { it.tag == type.channel }
    }

    fun cancelForUpdate() {
        ignoreAllExceptions {
            nm.cancel(Types.Update.id)
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
        val NewStalk = NotificationType(7000, "NEW_STALK", R.string.notification_channel_new_stalk)
        val NewNotification = NotificationType(7000, "NEW_NOTIFICATION", R.string.notification_channel_new_notification)
    }

    private data class NotificationId(val tag: String, val id: Int)
}

private val Message.groupId: String
    get() {
        return when (type) {
            MessageType.COMMENT -> "comment:$itemId"
            MessageType.MESSAGE -> "message:$name"
            else -> "$type:$id"
        }
    }

private class MessageNotificationConfig(context: Context, messages: List<Message>) {
    private val nh = NotificationHelperService(context)

    // select the most recent message
    private val message = messages.maxBy { it.creationTime }
            ?: throw IllegalArgumentException("Must contain at least one message.")

    // the type of all messages, if they all have the same type, null otherwise.
    val messageType: MessageType = message.type

    val notificationId: Int = when (messageType) {
        MessageType.STALK -> message.itemId.toInt()
        MessageType.COMMENT -> message.itemId.toInt()
        MessageType.MESSAGE -> message.senderId
        MessageType.NOTIFICATION -> message.id.toInt()
    }

    val type = when (messageType) {
        MessageType.STALK -> NotificationService.Types.NewStalk
        MessageType.MESSAGE -> NotificationService.Types.NewMessage
        MessageType.COMMENT -> NotificationService.Types.NewComment
        MessageType.NOTIFICATION -> NotificationService.Types.NewNotification
    }

    val title: String = when {
        messageType === MessageType.MESSAGE && messages.size > 1 -> {
            val more = context.getString(R.string.notification_hint_unread, messages.size)
            "${message.name} ($more)"
        }

        messageType === MessageType.COMMENT && messages.size == 1 ->
            context.getString(R.string.notify_new_comment_title)

        messageType === MessageType.MESSAGE ->
            message.name

        messageType === MessageType.COMMENT ->
            context.getString(R.string.notify_new_comments_title, messages.size)

        messageType === MessageType.STALK ->
            context.getString(R.string.notify_new_stalk_post, message.name)

        messageType === MessageType.NOTIFICATION ->
            context.getString(R.string.notify_new_system_notification)

        else -> "unreachable"
    }

    val contentText: CharSequence? = when (message.type) {
        MessageType.STALK -> context.getString(R.string.notify_hint_tap_to_open_post)

        MessageType.COMMENT -> buildSpannedString {
            bold { append(message.name).append(": ") }
            append(message.message)
        }

        else -> if (message.message.length > 120) message.message.take(119) + "…" else message.message
    }

    val contentIntent: PendingIntent = when (message.type) {
        MessageType.MESSAGE ->
            nh.inboxActivityIntent(InboxType.PRIVATE, message.name)

        MessageType.STALK ->
            nh.openItemIntent(message.itemId)

        else ->
            nh.inboxActivityIntent(InboxType.forMessageType(message.type))
    }

    val deleteIntent: PendingIntent = nh.markAsReadIntent(message)

    val icon: Bitmap? = nh.messageThumbnail(messages)

    val timestampWhen: Instant = messages.minBy { it.creationTime }!!.creationTime

    val action: NotificationCompat.Action? = when {
        messageType === MessageType.STALK ->
            nh.buildInboxAction(InboxType.STALK)

        messageType === MessageType.MESSAGE ->
            nh.buildReplyAction(notificationId, message)

        messageType === MessageType.COMMENT && messages.size == 1 ->
            nh.buildReplyAction(notificationId, message)

        else -> null
    }

    val style: NotificationCompat.Style = when (message.type) {
        MessageType.STALK -> {
            val bigPicture = nh.loadImage(message, bigSize = true)
            NotificationCompat.BigPictureStyle().also { style ->
                style.bigPicture(bigPicture)
                style.bigLargeIcon(null)
            }
        }

        MessageType.NOTIFICATION -> {
            NotificationCompat.BigTextStyle().also { style ->
                style.bigText(message.message)
            }
        }

        else -> inboxStyle(messages)
    }
}

private fun inboxStyle(messages: List<Message>): NotificationCompat.InboxStyle {
    return NotificationCompat.InboxStyle().also { style ->
        messages.sortedBy { it.creationTime }.takeLast(5).forEach { message ->
            val line = if (message.isComment) {
                buildSpannedString {
                    bold { append(message.name).append(": ") }
                    append(message.message.take(100))
                }
            } else {
                message.message
            }

            style.addLine(line)
        }
    }
}

class NotificationHelperService(val context: Context) {
    private val logger = Logger("NotificationHelperService")
    private val picasso: Picasso = context.injector.instance()

    fun inboxActivityIntent(inboxType: InboxType, conversationName: String? = null): PendingIntent {
        val intent = Intent(context, InboxActivity::class.java)
        intent.data = Uri.parse("inbox://${inboxType}/${conversationName}")
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

    fun markAsReadIntent(message: Message): PendingIntent {
        val intent = InboxNotificationCanceledReceiver.makeIntent(context, message)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * This builds the little "reply" action under a notification.
     */
    fun buildReplyAction(notificationId: Int, message: Message): NotificationCompat.Action {
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
     * This builds the little "go to inbox" action under a notification.
     */
    fun buildInboxAction(inboxType: InboxType, conversationName: String? = null): NotificationCompat.Action {
        val pendingIntent = inboxActivityIntent(inboxType, conversationName)

        // add everything as an action
        return NotificationCompat.Action.Builder(R.drawable.ic_action_email, context.getString(R.string.notify_to_inbox), pendingIntent)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .build()
    }

    fun openItemIntent(itemId: Long): PendingIntent {
        val intent = MainActivity.openItemIntent(context, itemId, feedType = FeedType.STALK)

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        return TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(intent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)!!
    }

    /**
     * Gets an optional "big" thumbnail for the given set of messages.
     */
    fun messageThumbnail(messages: List<Message>): Bitmap? {
        val message = messages.first()

        if (message.thumbnail?.isNotBlank() == true) {
            return loadImage(message)

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

    fun loadImage(message: Message, bigSize: Boolean = false): Bitmap? {
        checkNotMainThread()

        val uriHelper: UriHelper = UriHelper.of(context)

        val activeContentTypes = if (Settings.get().feedStartAtSfw) {
            setOf(ContentType.SFW)
        } else {
            Settings.get().contentType
        }

        val blurImage = ContentType.firstOf(message.flags) !in activeContentTypes

        val uri = if (bigSize) {
            if (message.image != null) {
                if (isVideoUri(message.image)) {
                    uriHelper.fullThumbnail(message.asThumbnail())
                } else {
                    uriHelper.image(message.itemId, message.image)
                }
            } else {
                uriHelper.thumbnail(message.asThumbnail())
            }
        } else {
            uriHelper.thumbnail(message.asThumbnail())
        }

        return try {
            return picasso.load(uri).run {
                if (blurImage) {
                    val radius = if (bigSize) 24 else 12
                    transform(BlurTransformation(radius))
                }

                if (bigSize) {
                    resize(512, 512)
                    onlyScaleDown()
                    centerCrop()
                }

                get()
            }

        } catch (ignored: IOException) {
            logger.warn { "Could not load thumbnail at url: $uri" }
            null
        }
    }
}