package com.pr0gramm.app.services

import android.content.Context
import android.content.Intent
import com.github.salomonbrys.kodein.android.KodeinBroadcastReceiver
import com.github.salomonbrys.kodein.instance
import org.joda.time.Instant


/**
 */
class InboxNotificationCanceledReceiver : KodeinBroadcastReceiver() {
    private val inboxService: InboxService by instance()

    override fun onBroadcastReceived(context: Context, intent: Intent) {
        val timestamp = intent.getLongExtra(EXTRA_MESSAGE_TIMESTAMP, 0)
        if (timestamp > 0) {
            inboxService.markAsRead(timestamp)
        }

        // track this action
        Track.notificationClosed("swiped")
    }

    companion object {
        val EXTRA_MESSAGE_TIMESTAMP = "InboxNotificationCanceledReceiver.messageTimestamp"

        fun makeIntent(context: Context, timestamp: Instant): Intent {
            val intent = Intent(context, InboxNotificationCanceledReceiver::class.java)
            intent.putExtra(InboxNotificationCanceledReceiver.EXTRA_MESSAGE_TIMESTAMP, timestamp.millis)
            return intent
        }
    }
}
