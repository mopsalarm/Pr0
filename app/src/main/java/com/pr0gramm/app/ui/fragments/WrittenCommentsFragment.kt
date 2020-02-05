package com.pr0gramm.app.ui.fragments

import androidx.lifecycle.lifecycleScope
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.api.pr0gramm.MessageConverter
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.MessageAdapter
import com.pr0gramm.app.ui.Pagination
import com.pr0gramm.app.ui.PaginationController
import com.pr0gramm.app.util.di.instance

/**
 */
class WrittenCommentsFragment : InboxFragment("WrittenCommentFragment") {
    private val userService: UserService by instance()

    override fun getContentAdapter(): Pair<MessageAdapter, Pagination<Message>> {
        val loader = apiMessageLoader { olderThan ->
            val name = userService.name ?: return@apiMessageLoader listOf()
            val userComments = inboxService.getUserComments(name, ContentType.AllSet, olderThan)
            userComments.comments.map {
                MessageConverter.of(userComments.user, it)
            }
        }

        // create and initialize the adapter
        val pagination = Pagination(lifecycleScope, loader)

        val adapter = MessageAdapter(
                R.layout.row_inbox_message, actionListener, userService.name,
                PaginationController(pagination, tailOffset = 32))

        return Pair(adapter, pagination)
    }
}
