package com.pr0gramm.app.ui.fragments

import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.FavedCommentService
import org.kodein.di.erased.instance

/**
 */
class FavedCommentFragment : AbstractMessageInboxFragment("FavedCommentFragment") {
    private val settings = Settings.get()
    private val favedCommentService: FavedCommentService by instance()

    override suspend fun loadContent(): List<Api.Message> {
        return favedCommentService.list(settings.contentType).map {
            FavedCommentService.commentToMessage(it)
        }
    }
}
