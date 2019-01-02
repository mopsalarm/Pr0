package com.pr0gramm.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pr0gramm.app.Instant
import com.pr0gramm.app.parcel.getFreezableExtra
import com.pr0gramm.app.parcel.putFreezable
import com.pr0gramm.app.util.bundle
import com.pr0gramm.app.util.di.injector


/**
 */
class InboxNotificationCanceledReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val inboxService: InboxService = context.injector.instance()

        val timestamp = intent.getFreezableExtra(EXTRA_MESSAGE_TIMESTAMP, Instant)
        if (timestamp != null) {
            inboxService.markAsRead(timestamp)
        }

        // track this action
        Track.inboxNotificationClosed("swiped")
    }

    companion object {
        private const val EXTRA_MESSAGE_TIMESTAMP = "InboxNotificationCanceledReceiver.messageTimestamp"

        fun makeIntent(context: Context, timestamp: Instant): Intent {
            return Intent(context, InboxNotificationCanceledReceiver::class.java).apply {
                replaceExtras(bundle {
                    putFreezable(InboxNotificationCanceledReceiver.EXTRA_MESSAGE_TIMESTAMP, timestamp)
                })
            }
        }
    }
}
