package com.pr0gramm.app.ui.fragments

import com.github.salomonbrys.kodein.instance
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.FavedCommentService
import com.pr0gramm.app.ui.MessageAdapter
import com.pr0gramm.app.ui.MessageView

/**
 */
class FavedCommentFragment : MessageInboxFragment("FavedCommentFragment") {
    private val settings = Settings.get()
    private val favedCommentService: FavedCommentService by instance()

    override fun newLoaderHelper(): LoaderHelper<List<Api.Message>> {
        return LoaderHelper.of {
            favedCommentService
                    .list(settings.contentType)
                    .map { comments -> comments.map { FavedCommentService.commentToMessage(it) } }
        }
    }
}
