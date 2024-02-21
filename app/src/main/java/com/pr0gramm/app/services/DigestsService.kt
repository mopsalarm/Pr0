package com.pr0gramm.app.services

import com.pr0gramm.app.api.pr0gramm.Api

class DigestsService(private val api: Api) {
    suspend fun digests(): List<Api.DigestsInbox.Digest> {
        return api.inboxDigests(older = null).digests
    }
}