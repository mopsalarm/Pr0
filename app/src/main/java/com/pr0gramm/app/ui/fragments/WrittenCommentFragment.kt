package com.pr0gramm.app.ui.fragments

import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.MessageConverter
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.MessageAdapter
import com.pr0gramm.app.ui.Pagination
import org.kodein.di.erased.instance

/**
 */
class WrittenCommentFragment : InboxFragment("WrittenCommentFragment") {
    private val userService: UserService by instance()

    override fun getContentAdapter(): RecyclerView.Adapter<*> {
        val loader = apiMessageLoader { olderThan ->
            // TODO use olderThan parameter.
            val name = userService.name ?: return@apiMessageLoader listOf()
            val userComments = inboxService.getUserComments(name, ContentType.AllSet, olderThan)
            userComments.comments.map {
                MessageConverter.of(userComments.user, it)
            }
        }

        // create and initialize the adapter
        val pagination = Pagination(this, loader, Pagination.State.hasMoreState(listOf()))
        return MessageAdapter(requireContext(),
                R.layout.row_inbox_message, actionListener, userService.name, pagination)
    }
}
