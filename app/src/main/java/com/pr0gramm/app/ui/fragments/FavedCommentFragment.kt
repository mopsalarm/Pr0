package com.pr0gramm.app.ui.fragments

import androidx.lifecycle.lifecycleScope
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.services.FavedCommentService
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.MessageAdapter
import com.pr0gramm.app.ui.Pagination
import com.pr0gramm.app.ui.PaginationController
import com.pr0gramm.app.util.di.instance

/**
 */
class FavedCommentFragment : InboxFragment("FavedCommentFragment") {
    private val settings = Settings.get()
    private val favedCommentService: FavedCommentService by instance()
    private val userService: UserService by instance()

    override fun getContentAdapter(): Pair<MessageAdapter, Pagination<Message>> {
        val loader = apiMessageLoader(requireContext()) { olderThan ->
            val username = userService.name
            if (username == null || !userService.isAuthorized) {
                return@apiMessageLoader listOf()
            }

            favedCommentService.list(settings.contentType, username, olderThan).map {
                FavedCommentService.commentToMessage(it)
            }
        }

        // create and initialize the adapter
        val pagination = Pagination(lifecycleScope, loader)

        val adapter = MessageAdapter(
                R.layout.row_inbox_message, actionListener, null,
                PaginationController(pagination))

        return Pair(adapter, pagination)
    }
}
