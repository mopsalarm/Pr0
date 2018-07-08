package com.pr0gramm.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pr0gramm.app.Instant
import com.pr0gramm.app.util.directKodein
import org.kodein.di.erased.instance


/**
 */
class InboxNotificationCanceledReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val inboxService: InboxService = context.directKodein.instance()

        val timestamp = intent.getLongExtra(EXTRA_MESSAGE_TIMESTAMP, 0)
        if (timestamp > 0) {
            inboxService.markAsRead(timestamp)
        }

        // track this action
        Track.inboxNotificationClosed("swiped")
    }

    companion object {
        const val EXTRA_MESSAGE_TIMESTAMP = "InboxNotificationCanceledReceiver.messageTimestamp"

        fun makeIntent(context: Context, timestamp: Instant): Intent {
            val intent = Intent(context, InboxNotificationCanceledReceiver::class.java)
            intent.putExtra(InboxNotificationCanceledReceiver.EXTRA_MESSAGE_TIMESTAMP, timestamp.millis)
            return intent
        }
    }
}
