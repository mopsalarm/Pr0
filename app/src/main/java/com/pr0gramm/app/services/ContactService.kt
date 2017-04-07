package com.pr0gramm.app.services

import com.pr0gramm.app.api.pr0gramm.Api
import rx.Completable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A simple service that allows sending a message to the pr0gramm support.
 */
@Singleton
class ContactService @Inject constructor(private val api: Api) {
    fun contactFeedback(email: String, subject: String, message: String): Completable {
        return api.contactSend(subject, email, message).toCompletable()
    }
}
