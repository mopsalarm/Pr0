package com.pr0gramm.app.ui.fragments

import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.MessageConverter
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.services.UserService
import org.kodein.di.erased.instance
import rx.Observable

/**
 */
class WrittenCommentFragment : AbstractMessageInboxFragment("WrittenCommentFragment") {
    private val userService: UserService by instance()

    override fun newLoaderHelper(): LoaderHelper<List<Api.Message>> {
        return LoaderHelper.of<List<Api.Message>> {
            val name = userService.name ?: return@of Observable.empty()

            inboxService
                    .getUserComments(name, ContentType.AllSet)
                    .map { userComments ->
                        userComments.comments.map {
                            MessageConverter.of(userComments.user, it)
                        }
                    }
        }
    }
}
