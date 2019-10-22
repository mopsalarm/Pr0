package com.pr0gramm.app.services

import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.db.AppDB
import com.pr0gramm.app.db.FollowState
import com.pr0gramm.app.ui.base.Async
import com.pr0gramm.app.ui.base.withBackgroundContext
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrDefault
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow


/**
 * Service to handle follow & subscription.
 */

class FollowService(private val api: Api, private val db: AppDB) {
    suspend fun update(action: FollowAction, userId: Long, name: String) {
        withBackgroundContext(NonCancellable) {
            when (action) {
                FollowAction.NONE ->
                    api.profileUnfollow(null, name)

                FollowAction.FOLLOW ->
                    api.profileFollow(null, name)

                FollowAction.SUBSCRIBED -> {
                    api.profileFollow(null, name)
                    api.profileSubscribe(null, name)
                }
            }

            db.followStateQueries.updateUser(userId, action.following, action.subscribed)
        }
    }

    fun isFollowing(userId: Long): Flow<FollowState> {
        val defaultValue = FollowState.Impl(userId, following = false, subscribed = false)

        return db.followStateQueries.forUser(userId)
                .asFlow()
                .mapToOneOrDefault(defaultValue, Async)
    }
}

enum class FollowAction(val following: Boolean, val subscribed: Boolean) {
    NONE(following = false, subscribed = false),
    FOLLOW(following = true, subscribed = false),
    SUBSCRIBED(following = true, subscribed = true)
}
