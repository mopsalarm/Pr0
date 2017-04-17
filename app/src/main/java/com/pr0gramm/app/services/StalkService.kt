package com.pr0gramm.app.services

import com.pr0gramm.app.api.pr0gramm.Api
import rx.Completable
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*


/**
 * Service to handle stalking.
 */

class StalkService(private val api: Api) {
    private val following = Collections.synchronizedSet(HashSet<String>())
    private val changes = PublishSubject.create<String>()

    fun follow(username: String): Completable {
        return api.profileFollow(null, username)
                .toCompletable()
                .doOnCompleted { markAsFollowing(username, true) }
    }

    fun unfollow(username: String): Completable {
        return api.profileUnfollow(null, username)
                .toCompletable()
                .doOnCompleted { markAsFollowing(username, false) }
    }

    fun markAsFollowing(username: String, following: Boolean) {
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
