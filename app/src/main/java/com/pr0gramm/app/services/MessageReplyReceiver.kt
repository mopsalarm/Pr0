package com.pr0gramm.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.util.Logger
import com.pr0gramm.app.util.catchAll
import com.pr0gramm.app.util.di.LazyInjectorAware
import com.pr0gramm.app.util.di.PropertyInjector
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.doInBackground

/**
 * Reply directly to a user
 */
class MessageReplyReceiver : BroadcastReceiver(), LazyInjectorAware {
    private val logger = Logger("MessageReplyReceiver")

    override val injector: PropertyInjector = PropertyInjector()

    private val inboxService: InboxService by instance()
    private val voteService: VoteService by instance()
    private val notificationService: NotificationService by instance()

    override fun onReceive(context: Context, intent: Intent) {
        injector.inject(context)

        // normal receiver info
        val receiverId = intent.getIntExtra("receiverId", 0)
        val receiverName = intent.getStringExtra("receiverName")

        // receiver infos for comments
        val itemId = intent.getLongExtra("itemId", 0)
        val commentId = intent.getLongExtra("commentId", 0)

        val text = getMessageText(intent)

        // validate parameters
        if (text.isEmpty() || receiverName.isNullOrEmpty()) {
            logger.error { "No receiver id or message." }
            return
        }

        // timestamp the original message was sent
        val messageCreated = Instant(intent.getLongExtra("messageCreated", -1))

        // decide if we are sending a message or a comment
        val isMessage = itemId == 0L || commentId == 0L

        // notification id to re-use for confirmation
        val notificationId = intent.getIntExtra("notificationId", 0)

        doInBackground {
            catchAll {
                if (isMessage) {
                    sendResponseToMessage(receiverId, text)
                } else {
                    sendResponseAsComment(itemId, commentId, text)
                }

                notificationService.showSendSuccessfulNotification(receiverName, notificationId)
            }

            markMessageAsRead(context, messageCreated)
        }
    }

    private suspend fun sendResponseAsComment(itemId: Long, commentId: Long, text: String) {
        voteService.postComment(itemId, commentId, text)
    }

    private suspend fun sendResponseToMessage(receiverId: Int, text: String) {
        inboxService.send(receiverId.toLong(), text)
    }

    private fun markMessageAsRead(context: Context, messageTimestamp: Instant) {
        val intent = InboxNotificationCanceledReceiver.makeIntent(context, messageTimestamp)
        context.sendBroadcast(intent)
    }

    private fun getMessageText(intent: Intent): String {
        return RemoteInput.getResultsFromIntent(intent)?.getCharSequence("msg")?.toString() ?: ""
    }

    companion object {
        fun makeIntent(context: Context, notificationId: Int, message: Api.Message): Intent {
            val intent = Intent(context, MessageReplyReceiver::class.java)
            intent.putExtra("notificationId", notificationId)

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
