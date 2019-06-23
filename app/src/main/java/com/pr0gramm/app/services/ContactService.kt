package com.pr0gramm.app.services

import com.pr0gramm.app.api.pr0gramm.Api

/**
 * A simple service that allows sending a message to the pr0gramm support.
 */
class ContactService(private val api: Api) {
    suspend fun post(email: String, subject: String, message: String) {
        api.contactSendAsync(subject, email, message)
    }

    suspend fun report(itemId: Long, comment: Long, reason: String) {
        api.reportAsync(null, itemId, comment, reason)
    }
}
