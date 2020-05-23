package com.pr0gramm.app.ui.views

import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.FollowState

interface PostActions {
    /**
     * Called if the user clicked on a tag.

     * @param tag The tag that was clicked.
     */
    fun onTagClicked(tag: Api.Tag)

    /**
     * Called if a user clicks on a username
     * @param username The username that was clicked.
     */
    fun onUserClicked(username: String)

    /**
     * The User wants to vote this tag.
     */
    fun voteTagClicked(tag: Api.Tag, vote: Vote): Boolean

    /**
     * The user wants to vote this post
     */
    fun votePostClicked(vote: Vote): Boolean

    /**
     * The user wants to write a new tag.
     */
    fun writeNewTagClicked()

    /**
     * Writes a new comment
     */
    suspend fun writeCommentClicked(text: String)

    /**
     * Follow the user
     */
    suspend fun updateFollowUser(follow: FollowState): Boolean

    /**
     * The user clicked on the button to collect the item.
     */
    fun collectClicked()
}