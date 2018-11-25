package com.pr0gramm.app.ui.fragments

import android.os.Bundle
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.NotificationService
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.InboxType
import com.pr0gramm.app.ui.MessageAdapter
import org.kodein.di.erased.instance

/**
 */
abstract class AbstractMessageInboxFragment(name: String) : InboxFragment<Api.Message>(name) {
    private val notificationService: NotificationService by instance()
    private val userService: UserService by instance()

    override suspend fun loadContent(): List<Api.Message> {
        return when (inboxType) {
            InboxType.ALL -> inboxService.inbox()
            InboxType.UNREAD -> inboxService.unreadMessages()
            else -> throw IllegalArgumentException()
        }
    }

    override fun displayMessages(recyclerView: androidx.recyclerview.widget.RecyclerView, messages: List<Api.Message>) {
        val adapter = recyclerView.adapter
        if (adapter is MessageAdapter) {
            adapter.setMessages(messages)
        } else {
            recyclerView.adapter = newMessageAdapter(messages)
        }

        // after showing messages, we'll just remove unread messages and resync
        if (activity != null) {
            notificationService.cancelForInbox()
        }
    }

    protected open fun newMessageAdapter(messages: List<Api.Message>): MessageAdapter {
        return MessageAdapter(R.layout.row_inbox_message, actionListener, userService.name, messages)
    }

    companion object {

        /**
         * Builds arguments to create a fragment for the given type.

         * @param inboxType The inbox type to create the fragment for.
         */
        fun buildArguments(inboxType: InboxType): Bundle {
            val args = Bundle()
            args.putInt(InboxFragment.ARG_INBOX_TYPE, inboxType.ordinal)
            return args
        }
    }
}
