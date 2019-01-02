package com.pr0gramm.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.views.SenderInfoView
import com.pr0gramm.app.util.Linkify
import com.pr0gramm.app.util.UserDrawables
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.use
import com.squareup.picasso.Picasso

/**
 */
class MessageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {
    private val userDrawables: UserDrawables = UserDrawables(context)
    private val admin: Boolean

    private val text: TextView
    private val image: ImageView
    private val sender: SenderInfoView

    private val picasso: Picasso?

    private val scoreVisibleThreshold = Instant.now() - Duration.hours(1)

    init {
        val layoutId = context.theme.obtainStyledAttributes(attrs, R.styleable.MessageView, 0, 0).use {
            it.getResourceId(R.styleable.MessageView_viewLayout, R.layout.message_view)
        }

        View.inflate(context, layoutId, this)
        text = find(R.id.message_text)
        image = find(R.id.message_image)
        sender = find(R.id.message_sender_info)

        if (!isInEditMode) {
            picasso = context.injector.instance()

            val userService = context.injector.instance<UserService>()
            admin = userService.userIsAdmin
        } else {
            admin = false
            picasso = null
        }
    }

    fun setAnswerClickedListener(listener: (View) -> Unit) {
        sender.setOnAnswerClickedListener(OnClickListener { listener(it) })
    }

    fun setOnSenderClickedListener(listener: () -> Unit) {
        sender.setOnSenderClickedListener(listener)
    }

    @JvmOverloads
    fun update(message: Api.Message, name: String? = null) {
        // set the type. if we have an item, we  have a comment
        val isComment = message.itemId != 0L

        // the text of the message
        Linkify.linkifyClean(text, message.message)

        // draw the image for this post
        if (isComment) {
            val url = "https://thumb.pr0gramm.com/" + message.thumbnail!!
            picasso?.load(url)?.into(image)
        } else {
            picasso?.cancelRequest(image)

            // set a colored drawable with the first two letters of the user
            image.setImageDrawable(userDrawables.drawable(message))
        }

        // show the points
        val visible = name != null && message.name.equals(name, ignoreCase = true)
                || message.creationTime.isBefore(scoreVisibleThreshold)

        // sender info
        sender.setSenderName(message.name, message.mark)
        sender.setDate(message.creationTime)

        if (isComment) {
            if (admin || visible) {
                sender.setPoints(message.score)
            } else {
                sender.setPointsUnknown()
            }
        } else {
            sender.hidePointView()
        }
    }
}
