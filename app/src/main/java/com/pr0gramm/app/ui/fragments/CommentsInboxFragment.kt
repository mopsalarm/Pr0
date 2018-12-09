package com.pr0gramm.app.ui.fragments

import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.NotificationService
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.MessageAdapter
import com.pr0gramm.app.ui.Pagination
import com.pr0gramm.app.ui.StateTransform
import org.kodein.di.erased.instance

open class CommentsInboxFragment : InboxFragment("MessageInboxFragment") {
    private val userService: UserService by instance()
    private val notificationService: NotificationService by instance()

    override fun getContentAdapter(): RecyclerView.Adapter<*> {
        val loader = apiMessageLoader { inboxService.comments(it) }
        val pagination = Pagination(this, loader, Pagination.State.hasMoreState())

        notificationService.cancelForInbox()

        return MessageAdapter(requireContext(),
                R.layout.row_inbox_message, actionListener, userService.name, pagination)
    }
}

fun apiMessageLoader(loader: suspend (Instant?) -> List<Api.Message>): Pagination.Loader<Api.Message> {
    class MessagePaginationLoader : Pagination.Loader<Api.Message>() {
        override suspend fun loadAfter(currentValues: List<Api.Message>): StateTransform<Api.Message> {
            val olderThan = currentValues.lastOrNull()?.creationTime
            val messages = loader(olderThan)

            return { state ->
                val combined = state.values + messages
                state.copy(
                        values = combined,
                        tailState = Pagination.EndState(hasMore = messages.size > 10))
            }
        }

        override suspend fun loadBefore(currentValues: List<Api.Message>): StateTransform<Api.Message> {
            return { state -> state.copy(headState = Pagination.EndState()) }
        }
    }

    return MessagePaginationLoader()
}
