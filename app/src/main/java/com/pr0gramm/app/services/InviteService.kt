package com.pr0gramm.app.services

import android.util.Patterns
import com.pr0gramm.app.api.pr0gramm.Api
import rx.Completable
import rx.Observable

/**
 */
class InviteService(private val api: Api) {
    /**
     * Send an invite to the given email address.
     */
    fun send(email: String): Completable {
        val matcher = Patterns.EMAIL_ADDRESS.matcher(email)
        if (!matcher.matches())
            return Completable.error(InviteException("email"))

        val result = api.invite(null, email).flatMapCompletable { response ->
            val error = response.error
            if (error != null) {
                Completable.error(InviteException(error))
            } else {
                Completable.complete()
            }
        }

        return result.toCompletable()
    }

    /**
     * Returns an observable producing the invites of the current user once.
     */
    fun invites(): Observable<Invites> {
        return api.accountInfo().map<Invites> { info ->
            Invites(info.account.invites, info.invited)
        }
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
