package com.pr0gramm.app.services

import android.util.Patterns
import com.pr0gramm.app.api.pr0gramm.Api

/**
 */
class InviteService(private val api: Api) {
    /**
     * Send an invite to the given email address.
     */
    suspend fun send(email: String) {
        val matcher = Patterns.EMAIL_ADDRESS.matcher(email)
        if (!matcher.matches())
            throw InviteException("email")

        val response = api.inviteAsync(null, email).await()
        if (response.error != null) {
            throw InviteException(response.error)
        }
    }

    /**
     * Returns an observable producing the invites of the current user once.
     */
    suspend fun invites(): Invites {
        val info = api.accountInfoAsync().await()
        return Invites(info.account.invites, info.invited)
    }

    class InviteException(private val errorCode: String) : RuntimeException() {
        fun noMoreInvites(): Boolean {
            return "noInvites".equals(errorCode, ignoreCase = true)
        }

        fun emailFormat(): Boolean {
            return "emailInvalid".equals(errorCode, ignoreCase = true)
        }

        fun emailInUse(): Boolean {
            return "emailInUse".equals(errorCode, ignoreCase = true)
        }
    }

    data class Invites(val inviteCount: Int, val invited: List<Api.AccountInfo.Invite>)
}
