package com.pr0gramm.app.ui.fragments

import android.app.Activity
import android.content.Intent
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.MessageConverter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.MainActivity
import com.pr0gramm.app.ui.MessageAdapter

/**
 * Extends a normal [MessageAdapter] to display a users comment. If you click
 * one of those comments, it will open the post/comment.
 */
class UserCommentsAdapter(private val activity: Activity) :
        MessageAdapter(activity, emptyList(), null, R.layout.user_info_comment) {

    override fun onBindViewHolder(view: MessageAdapter.MessageViewHolder, position: Int) {
        super.onBindViewHolder(view, position)
        view.itemView.setOnClickListener { v ->
            val message = this.messages[position]

            val uriHelper = UriHelper.of(activity)
            val uri = uriHelper.post(FeedType.NEW, message.itemId(), message.id())
            val intent = Intent(Intent.ACTION_VIEW, uri, activity, MainActivity::class.java)
            activity.startActivity(intent)
        }
    }

    fun setComments(user: Api.Info.User, comments: List<Api.UserComments.UserComment>) {
        setMessages(comments.map { MessageConverter.of(user, it) })
    }
}
