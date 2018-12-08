package com.pr0gramm.app.ui.fragments

import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.services.FavedCommentService
import com.pr0gramm.app.ui.MessageAdapter
import com.pr0gramm.app.ui.Pagination
import org.kodein.di.erased.instance

/**
 */
class FavedCommentFragment : InboxFragment("FavedCommentFragment") {
    private val settings = Settings.get()
    private val favedCommentService: FavedCommentService by instance()


    override fun getContentAdapter(): RecyclerView.Adapter<*> {
        val loader = apiMessageLoader { olderThan ->
            // TODO use olderThan parameter.
            favedCommentService.list(settings.contentType).map {
                FavedCommentService.commentToMessage(it)
            }
        }

        // create and initialize the adapter
        val pagination = Pagination(this, loader, Pagination.State.hasMoreState())
        return MessageAdapter(R.layout.row_inbox_message, actionListener, null, pagination)
    }
}
