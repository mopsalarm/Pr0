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

class MessageInboxFragment : InboxFragment("MessageInboxFragment") {
    private val userService: UserService by instance()
    private val notificationService: NotificationService by instance()

    override fun getContentAdapter(): RecyclerView.Adapter<*> {
        val loader = apiMessageLoader { inboxService.inbox(it) }
        val pagination = Pagination(this, loader, Pagination.State.hasMoreState(listOf(), 0))

        // create and initialize the adapter
        val adapter = MessageAdapter(R.layout.row_inbox_message, actionListener, userService.name, pagination)
        adapter.initialize()

        notificationService.cancelForInbox()

        return adapter
    }
}

fun apiMessageLoader(loader: suspend (Instant?) -> List<Api.Message>): Pagination.Loader<List<Api.Message>> {
    class MessagePaginationLoader : Pagination.Loader<List<Api.Message>> {
        override suspend fun loadAfter(currentValue: List<Api.Message>): StateTransform<List<Api.Message>> {
            val olderThan = currentValue.lastOrNull()?.creationTime
            val messages = loader(olderThan)

            return { state ->
                val combined = state.value + messages
                state.copy(
                        value = combined, valueCount = combined.size,
                        tailState = Pagination.EndState(hasMore = messages.size > 10))
            }
        }

        override suspend fun loadBefore(currentValue: List<Api.Message>): StateTransform<List<Api.Message>> {
            return { state -> state.copy(headState = Pagination.EndState()) }
        }
    }

    return MessagePaginationLoader()
}
