package com.pr0gramm.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.api.pr0gramm.MessageType
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.views.SenderInfoView
import com.pr0gramm.app.util.Linkify
import com.pr0gramm.app.util.UserDrawables
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.findOptional
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

    private val messageType: TextView?

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

        messageType = findOptional(R.id.message_type)

        if (!isInEditMode) {
            picasso = context.injector.instance()

            val userService = context.injector.instance<UserService>()
            admin = userService.userIsAdmin
        } else {
            admin = false
            picasso = null
        }
    }

    fun setAnswerClickedListener(@StringRes text: Int, listener: (View) -> Unit) {
        sender.setOnAnswerClickedListener(text, OnClickListener { listener(it) })
    }

    fun clearAnswerClickedListener() {
        sender.clearOnAnswerClickedListener()
    }

    fun setOnSenderClickedListener(listener: () -> Unit) {
        sender.setOnSenderClickedListener(listener)
    }

    @JvmOverloads
    fun update(message: Message, name: String? = null) {
        // the text of the message
        Linkify.linkifyClean(text, message.message)

        // draw the image for this post
        val thumbnail = message.thumbnail
        if (thumbnail != null) {
            val contentTypes = Settings.contentType
            val blurImage = ContentType.firstOf(message.flags) !in contentTypes

            val url = "https://thumb.pr0gramm.com/$thumbnail"
            picasso?.load(url)?.let { req ->
                if (blurImage) {
                    req.transform(BlurTransformation(12))
                }

                req.placeholder(R.color.grey_800)
                req.into(image)
            }
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

        // message type if available.
        messageType?.let { messageType ->
            val type = when (message.type) {
                MessageType.COMMENT -> R.string.message_type_comment
                MessageType.MESSAGE -> R.string.message_type_message
                MessageType.STALK -> R.string.message_type_stalk
                else -> R.string.message_type_notification
            }

            messageType.setText(type)
        }

        // set the type. if we have an item, we  have a comment
        if (message.type === MessageType.COMMENT) {
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
