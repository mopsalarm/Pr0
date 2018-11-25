package com.pr0gramm.app.ui.fragments

import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.MessageConverter
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.services.UserService
import org.kodein.di.erased.instance

/**
 */
class WrittenCommentFragment : AbstractMessageInboxFragment("WrittenCommentFragment") {
    private val userService: UserService by instance()

    override suspend fun loadContent(): List<Api.Message> {
        val name = userService.name ?: return listOf()

        val userComments = inboxService.getUserComments(name, ContentType.AllSet)
        return userComments.comments.map {
            MessageConverter.of(userComments.user, it)
        }
    }
}
