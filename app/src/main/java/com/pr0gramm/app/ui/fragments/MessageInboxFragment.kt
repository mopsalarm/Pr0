package com.pr0gramm.app.ui.fragments

import android.os.Bundle
import android.support.v7.widget.RecyclerView
import com.github.salomonbrys.kodein.instance

import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.NotificationService
import com.pr0gramm.app.ui.InboxType
import com.pr0gramm.app.ui.MessageAdapter

/**
 */
open class MessageInboxFragment(name: String = "MessageInboxFragment") : InboxFragment<Api.Message>(name) {
    private val notificationService: NotificationService by instance()

    override fun newLoaderHelper(): LoaderHelper<List<Api.Message>> {
        return LoaderHelper.of {
            when (inboxType) {
                InboxType.ALL -> inboxService.inbox
                InboxType.UNREAD -> inboxService.unreadMessages
                else -> throw IllegalArgumentException()
            }
        }
    }

    override fun displayMessages(recyclerView: RecyclerView, messages: List<Api.Message>) {
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
        val adapter = MessageAdapter(R.layout.row_inbox_message, activity, messages)
        adapter.actionListener = actionListener
        return adapter
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
