package com.pr0gramm.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import com.google.common.base.Ascii
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.views.SenderInfoView
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.SenderDrawableProvider
import com.pr0gramm.app.util.use
import com.squareup.picasso.Picasso
import kotterknife.bindOptionalView
import kotterknife.bindView
import org.joda.time.Hours
import org.joda.time.Instant.now

/**
 */
class MessageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : RelativeLayout(context, attrs, defStyleAttr) {
    private val senderDrawableProvider: SenderDrawableProvider = SenderDrawableProvider(context)
    private val admin: Boolean

    private val text: TextView by bindView(R.id.message_text)
    private val image: ImageView by bindView(R.id.message_image)
    private val sender: SenderInfoView by bindView(R.id.message_sender_info)
    private val type: TextView? by bindOptionalView(R.id.message_type)

    private val picasso: Picasso?

    private val scoreVisibleThreshold = now().minus(Hours.ONE.toStandardDuration())

    init {
        val layoutId = context.theme.obtainStyledAttributes(attrs, R.styleable.MessageView, 0, 0).use {
            it.getResourceId(R.styleable.MessageView_viewLayout, R.layout.message_view)
        }

        View.inflate(context, layoutId, this)

        if (!isInEditMode) {
            picasso = context.appKodein().instance()

            val userService = context.appKodein().instance<UserService>()
            admin = userService.userIsAdmin
        } else {
            admin = false
            picasso = null
        }
    }

    fun setAnswerClickedListener(listener: OnClickListener?) {
        sender.setOnAnswerClickedListener(listener)
    }

    fun setOnSenderClickedListener(listener: () -> Unit) {
        sender.setOnSenderClickedListener(listener)
    }

    @JvmOverloads fun update(message: Api.Message, name: String? = null, pointsVisibility: PointsVisibility = PointsVisibility.CONDITIONAL) {
        // set the type. if we have an item, we  have a comment
        val isComment = message.itemId() != 0L
        type?.let { type ->
            type.text = if (isComment) {
                context.getString(R.string.inbox_message_comment)
            } else {
                context.getString(R.string.inbox_message_private)
            }
        }

        // the text of the message
        AndroidUtility.linkifyClean(text, message.message())

        // draw the image for this post
        if (isComment) {
            val url = "http://thumb.pr0gramm.com/" + message.thumbnail()!!
            picasso?.load(url)?.into(image)
        } else {
            picasso?.cancelRequest(image)

            // set a colored drawable with the first two letters of the user
            image.setImageDrawable(senderDrawableProvider.makeSenderDrawable(message))
        }

        // show the points
        val visible = name != null && Ascii.equalsIgnoreCase(message.name(), name)
                || message.creationTime().isBefore(scoreVisibleThreshold)

        // sender info
        sender.setSenderName(message.name(), message.mark())
        sender.setDate(message.creationTime())

        if ((admin || pointsVisibility != PointsVisibility.NEVER) && isComment) {
            if (admin || pointsVisibility == PointsVisibility.ALWAYS || visible) {
                sender.setPoints(message.score())
            } else {
                sender.setPointsUnknown()
            }
        } else {
            sender.hidePointView()
        }
    }

    enum class PointsVisibility {
        ALWAYS, CONDITIONAL, NEVER
    }
}
