package com.pr0gramm.app.ui.fragments

import android.os.Bundle
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.NotificationService
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.MessageAdapter
import com.pr0gramm.app.ui.Pagination
import com.pr0gramm.app.ui.PaginationController
import com.pr0gramm.app.util.di.instance

open class CommentsInboxFragment : InboxFragment("CommentsInboxFragment") {
    private val userService: UserService by instance()
    private val notificationService: NotificationService by instance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationService.cancelForInbox()
    }

    override fun getContentAdapter(): Pair<MessageAdapter, Pagination<Api.Message>> {
        val loader = apiMessageLoader { inboxService.comments(it) }
        val pagination = Pagination(this, loader)

        val adapter = MessageAdapter(
                R.layout.row_inbox_message, actionListener, userService.name,
                PaginationController(pagination, tailOffset = 32))

        return Pair(adapter, pagination)
    }
}

fun apiMessageLoader(loader: suspend (Instant?) -> List<Api.Message>): Pagination.Loader<Api.Message> {
    class MessagePaginationLoader : Pagination.Loader<Api.Message>() {

        override suspend fun loadAfter(currentValue: Api.Message?): Pagination.Page<Api.Message> {
            val messages = loader(currentValue?.creationTime)
            return Pagination.Page(messages, messages.lastOrNull()?.takeIf { messages.size > 10 })
        }
    }

    return MessagePaginationLoader()
}
