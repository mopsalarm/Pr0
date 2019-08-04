package com.pr0gramm.app.services

import com.pr0gramm.app.api.pr0gramm.Api
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*


/**
 * Service to handle stalking.
 */

class StalkService(private val api: Api) {
    private val following = Collections.synchronizedSet(HashSet<String>())
    private val changes = PublishSubject.create<String>()

    suspend fun follow(username: String) {
        api.profileFollow(null, username)
        markAsFollowing(username, true)
    }

    suspend fun unfollow(username: String) {
        api.profileUnfollow(null, username)
        markAsFollowing(username, false)
    }

    private fun markAsFollowing(username: String, following: Boolean) {
        val changed = if (following) {
            this.following.add(username.toLowerCase())
        } else {
            this.following.remove(username.toLowerCase())
        }

        if (changed) {
            changes.onNext(username.toLowerCase())
        }
    }

    fun isFollowing(username: String): Boolean {
        return following.contains(username.toLowerCase())
    }

    fun changes(): Observable<String> {
        return changes.asObservable()
    }
}
