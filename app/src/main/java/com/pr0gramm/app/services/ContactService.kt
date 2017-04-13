package com.pr0gramm.app.services

import com.pr0gramm.app.api.pr0gramm.Api
import rx.Completable

/**
 * A simple service that allows sending a message to the pr0gramm support.
 */
class ContactService(private val api: Api) {
    fun post(email: String, subject: String, message: String): Completable {
        return api.contactSend(subject, email, message).toCompletable()
    }
}
