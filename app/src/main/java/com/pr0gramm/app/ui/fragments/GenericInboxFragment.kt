package com.pr0gramm.app.ui.fragments

import android.content.Context
import androidx.lifecycle.lifecycleScope
import com.pr0gramm.app.Instant
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.db.FeedItemInfoQueries
import com.pr0gramm.app.services.NotificationService
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.sync.SyncWorker
import com.pr0gramm.app.ui.MessageAdapter
import com.pr0gramm.app.ui.Pagination
import com.pr0gramm.app.ui.PaginationController
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.optionalFragmentArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.TimeUnit

open class GenericInboxFragment() : InboxFragment("GenericInboxFragment") {
    private val userService: UserService by instance()
    private val notificationService: NotificationService by instance()

    var messageType: String? by optionalFragmentArgument()

    constructor(messageType: String) : this() {
        this.messageType = messageType
    }

    override fun getContentAdapter(): Pair<MessageAdapter, Pagination<Message>> {
        val loader = apiMessageLoader(requireContext(), syncOnLoad = true) { olderThan ->
            when (this.messageType) {
                MessageTypeComments ->
                    inboxService.fetchComments(olderThan)

                MessageTypeNotifications ->
                    inboxService.fetchNotifications(olderThan)

                MessageTypeStalk ->
                    inboxService.fetchFollows(olderThan)

                else ->
                    inboxService.fetchAll(olderThan)
            }
        }

        val layoutId = when (this.messageType) {
            null -> R.layout.row_inbox_all_message
            else -> R.layout.row_inbox_message
        }

        val pagination = Pagination(lifecycleScope, loader)

        val adapter = MessageAdapter(
                layoutId, actionListener, userService.name,
                PaginationController(pagination, tailOffset = 32))

        return Pair(adapter, pagination)
    }

    override fun onResume() {
        super.onResume()
        notificationService.cancelForUnreadComments()
    }

    companion object {
        val MessageTypeComments: String = "comments"
        val MessageTypeNotifications: String = "notifications"
        val MessageTypeStalk: String = "follows"
    }
}

fun apiMessageLoader(ctx: Context, syncOnLoad: Boolean = false, loader: suspend (Instant?) -> List<Message>): Pagination.Loader<Message> {
    class MessagePaginationLoader : Pagination.Loader<Message>() {
        private val itemQueries = ctx.injector.instance<FeedItemInfoQueries>()
        private val logger = Logger("MessagePaginationLoader")

        override suspend fun loadAfter(currentValue: Message?): Pagination.Page<Message> {
            var messages = loader(currentValue?.creationTime)

            val messagesWithUnknownFlags = messages.filter { m -> m.itemId > 0L && m.flags == 0 }
            if (messagesWithUnknownFlags.isNotEmpty()) {
                val ids = messagesWithUnknownFlags.mapTo(HashSet()) { msg -> msg.itemId }

                val cached = runInterruptible(Dispatchers.IO) {
                    itemQueries.lookup(ids).executeAsList()
                }

                val byId = cached.associateBy(
                        keySelector = { item -> item.id },
                        valueTransform = { item -> item.flags },
                )

                logger.debug { "Found ${cached.size} of ${ids.size} cached items" }

                // update messages and add missing flags.
                messages = messages.map { msg ->
                    msg.copy(flags = byId[msg.itemId] ?: msg.flags)
                }
            }


            if (syncOnLoad) {
                // inbox numbers might have changed, better update now.
                SyncWorker.scheduleNextSyncIn(ctx, delay = 3, unit = TimeUnit.SECONDS, sourceTag = "inbox")
            }

            return Pagination.Page(messages, messages.lastOrNull()?.takeIf { messages.size > 10 })
        }
    }

    return MessagePaginationLoader()
}
