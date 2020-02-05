package com.pr0gramm.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.pr0gramm.app.Instant
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.parcel.getFreezableExtra
import com.pr0gramm.app.parcel.putFreezable
import com.pr0gramm.app.util.bundle
import com.pr0gramm.app.util.di.injector


/**
 */
class InboxNotificationCanceledReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val inboxService: InboxService = context.injector.instance()

        val unreadId = intent.getStringExtra(EXTRA_MESSAGE_UNREAD_ID) ?: return
        val timestamp = intent.getFreezableExtra(EXTRA_MESSAGE_TIMESTAMP, Instant) ?: return

        // now mark message as read
        inboxService.markAsRead(unreadId, timestamp)

        // track this action
        Track.inboxNotificationClosed("swiped")
    }

    companion object {
        private const val EXTRA_MESSAGE_TIMESTAMP = "messageTimestamp"
        private const val EXTRA_MESSAGE_UNREAD_ID = "messageUnreadId"

        fun makeIntent(context: Context, message: Message): Intent {
            return Intent(context, InboxNotificationCanceledReceiver::class.java).apply {
                data = Uri.parse("view://${message.unreadId}")

                replaceExtras(bundle {
                    putString(InboxNotificationCanceledReceiver.EXTRA_MESSAGE_UNREAD_ID, message.unreadId)
                    putFreezable(InboxNotificationCanceledReceiver.EXTRA_MESSAGE_TIMESTAMP, message.creationTime)
                })
            }
        }
    }
}
