package com.pr0gramm.app.services

import android.content.Context
import android.content.Intent
import android.support.v4.app.RemoteInput
import com.github.salomonbrys.kodein.android.KodeinBroadcastReceiver
import com.github.salomonbrys.kodein.instance
import com.google.common.base.Strings
import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.util.BackgroundScheduler
import org.slf4j.LoggerFactory
import rx.Completable

/**
 * Reply directly to a user
 */
class MessageReplyReceiver : KodeinBroadcastReceiver() {
    private val inboxService: InboxService by instance()
    private val voteService: VoteService by instance()
    private val notificationService: NotificationService by instance()

    override fun onBroadcastReceived(context: Context, intent: Intent) {
        // normal receiver info
        val receiverId = intent.getIntExtra("receiverId", 0)
        val receiverName = intent.getStringExtra("receiverName")

        // receiver infos for comments
        val itemId = intent.getLongExtra("itemId", 0)
        val commentId = intent.getLongExtra("commentId", 0)

        val text = getMessageText(intent)

        // validate parameters
        if (Strings.isNullOrEmpty(text) || Strings.isNullOrEmpty(receiverName)) {
            logger.error("No receiver id or message.")
            return
        }

        // timestamp the original message was sent
        val messageCreated = Instant(intent.getLongExtra("messageCreated", -1))

        // decide if we are sending a message or a comment
        val isMessage = itemId == 0L || commentId == 0L

        val result = if (isMessage) {
            sendResponseToMessage(receiverId, text)
        } else {
            sendResponseAsComment(itemId, commentId, text)
        }

        // and handle the result.
        result.subscribeOn(BackgroundScheduler.instance()).onErrorComplete().subscribe {
            notificationService.showSendSuccessfulNotification(receiverName)
            markMessageAsRead(context, messageCreated)
        }
    }

    internal fun sendResponseAsComment(itemId: Long, commentId: Long, text: String): Completable {
        return voteService.postComment(itemId, commentId, text).toCompletable()
    }

    internal fun sendResponseToMessage(receiverId: Int, text: String): Completable {
        return inboxService.send(receiverId.toLong(), text)
    }

    private fun markMessageAsRead(context: Context, messageTimestamp: Instant) {
        val intent = InboxNotificationCanceledReceiver.makeIntent(context, messageTimestamp)
        context.sendBroadcast(intent)
    }

    private fun getMessageText(intent: Intent): String {
        return RemoteInput.getResultsFromIntent(intent)?.getCharSequence("msg")?.toString() ?: ""
    }

    companion object {
        private val logger = LoggerFactory.getLogger("MessageReplyReceiver")

        fun makeIntent(context: Context, message: Api.Message): Intent {
            val intent = Intent(context, MessageReplyReceiver::class.java)
            if (message.isComment) {
                intent.putExtra("itemId", message.itemId)
                intent.putExtra("commentId", message.commentId)
            }

            intent.putExtra("receiverId", message.senderId)
            intent.putExtra("receiverName", message.name)
            intent.putExtra("messageCreated", message.creationTime.millis)
            return intent
        }
    }
}
