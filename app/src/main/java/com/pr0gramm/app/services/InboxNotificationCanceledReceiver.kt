package com.pr0gramm.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import com.pr0gramm.app.Dagger

import org.joda.time.Instant

import javax.inject.Inject

/**
 */
class InboxNotificationCanceledReceiver : BroadcastReceiver() {

    @Inject
    internal lateinit var inboxService: InboxService

    override fun onReceive(context: Context, intent: Intent) {
        Dagger.appComponent(context).inject(this)

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
