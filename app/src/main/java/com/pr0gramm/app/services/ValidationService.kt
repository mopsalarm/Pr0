package com.pr0gramm.app.services

import com.pr0gramm.app.api.pr0gramm.Api

class ValidationService(private val api: Api) {
    suspend fun validateUser(uriToken: String): Boolean {
        return api.userValidate(uriToken).success
    }

}