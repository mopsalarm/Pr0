package com.pr0gramm.app.services

import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.db.AppDB
import com.pr0gramm.app.ui.base.Async
import com.pr0gramm.app.ui.base.withBackgroundContext
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


/**
 * Service to handle follow & subscription.
 */

class FollowService(private val api: Api, private val db: AppDB) {
    suspend fun update(state: FollowState, userId: Long, name: String) {
        withBackgroundContext(NonCancellable) {
            when (state) {
                FollowState.NONE ->
                    api.profileUnfollow(null, name)

                FollowState.FOLLOW ->
                    api.profileFollow(null, name)

                FollowState.SUBSCRIBED -> {
                    api.profileFollow(null, name)
                    api.profileSubscribe(null, name)
                }
            }

            db.userFollowEntryQueries.updateUser(userId, state.ordinal)
        }
    }

    fun getState(userId: Long): Flow<FollowState> {
        return db.userFollowEntryQueries.forUser(userId)
                .asFlow()
                .mapToOneOrNull(Async)
                .map { value -> mapToState(value?.state) }
    }

    private fun mapToState(value: Int?): FollowState {
        val idx = value ?: return FollowState.NONE
        return FollowState.values().getOrNull(idx) ?: FollowState.NONE
    }
}

/**
 * Follow state, do not reorder, ordinal is used in database..
 */
enum class FollowState(val following: Boolean, val subscribed: Boolean) {
    NONE(following = false, subscribed = false),
    FOLLOW(following = true, subscribed = false),
    SUBSCRIBED(following = true, subscribed = true)
}
