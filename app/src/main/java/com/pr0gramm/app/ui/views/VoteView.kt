package com.pr0gramm.app.ui.views

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import com.pr0gramm.app.R
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.DrawableCache
import com.pr0gramm.app.util.dp
import com.pr0gramm.app.util.getColorCompat
import com.pr0gramm.app.util.getStyledResourceId
import com.pr0gramm.app.util.use

enum class VoteIconColor {
    NEUTRAL,
    MARKED_UP,
    MARKED_DOWN,
    INACTIVE,
}

private class VoteIcon(val iconId: Int, val color: VoteIconColor, val rotated: Boolean = false)

private class VoteState(val nextVote: Vote, vararg args: Pair<Vote, VoteIcon>) {
    val icons = args.toMap()
}

private val iconsDef = mapOf(
        Vote.UP to VoteState(Vote.NEUTRAL,
                Vote.NEUTRAL to VoteIcon(R.drawable.ic_vote_up, VoteIconColor.NEUTRAL),
                Vote.UP to VoteIcon(R.drawable.ic_vote_up, VoteIconColor.MARKED_UP, rotated = true),
                Vote.DOWN to VoteIcon(R.drawable.ic_vote_up, VoteIconColor.INACTIVE)
        ),

        Vote.DOWN to VoteState(Vote.NEUTRAL,
                Vote.NEUTRAL to VoteIcon(R.drawable.ic_vote_down, VoteIconColor.NEUTRAL),
                Vote.UP to VoteIcon(R.drawable.ic_vote_down, VoteIconColor.INACTIVE),
                Vote.DOWN to VoteIcon(R.drawable.ic_vote_down, VoteIconColor.MARKED_DOWN, rotated = true)
        )
)

/**
 * A plus and a minus sign to handle votes.
 */
class VoteView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ViewGroup(context, attrs, defStyleAttr) {
    private val views: Map<Vote, AppCompatImageView>

    private var voteIconSize: Int
    private var spaceSize: Int
    private var orientationIsVertical: Boolean

    private var voteState: Vote = Vote.NEUTRAL

    var markedColorUp: Int
        private set

    var markedColorDown: Int
        private set

    var defaultColor: Int
        private set

    private var wasAnimated: Boolean = false

    var onVote: (Vote) -> Boolean = { false }

    val vote: Vote get() = voteState

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.VoteView, 0, 0).use { a ->
            orientationIsVertical = a.getInteger(R.styleable.VoteView_orientation, 0) == 1
            voteIconSize = a.getDimensionPixelSize(R.styleable.VoteView_textSize, context.dp(24))
            spaceSize = a.getDimensionPixelSize(R.styleable.VoteView_spacing, context.dp(4))

            markedColorUp = a.getColor(R.styleable.VoteView_markedColor, context.getColorCompat(ThemeHelper.accentColor))
            markedColorDown = a.getColor(R.styleable.VoteView_markedColorDown, context.getColorCompat(R.color.white))
            defaultColor = a.getColor(R.styleable.VoteView_defaultColor, context.getColorCompat(R.color.white))
        }

        // ripple effect
        val backgroundId = context.getStyledResourceId(android.R.attr.selectableItemBackgroundBorderless)

        views = listOf(Vote.UP, Vote.DOWN).associateWith { vote ->
            val def = iconsDef.getValue(vote)

            // create the actual view
            val view = AppCompatImageView(context)
            view.setBackgroundResource(backgroundId)
            view.setImageDrawable(iconOf(def.icons.getValue(Vote.NEUTRAL)))
            view.setOnClickListener { triggerVoteClicked(vote) }

            view
        }


        views.values.forEach { addView(it) }

        if (isInEditMode) {
            setVoteState(Vote.UP, false)
        } else {
            // set initial voting state
            setVoteState(Vote.NEUTRAL, true)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val longAxis = views.size * voteIconSize + ((views.size - 1) * spaceSize)
        val shortAxis = voteIconSize

        val paddingHorizontal = paddingLeft + paddingRight
        val paddingVertical = paddingTop + paddingBottom

        val width: Int
        val height: Int
        if (orientationIsVertical) {
            width = shortAxis + paddingHorizontal
            height = longAxis + paddingVertical
        } else {
            width = longAxis + paddingHorizontal
            height = shortAxis + paddingVertical
        }

        setMeasuredDimension(
                View.resolveSizeAndState(width, widthMeasureSpec, 0),
                View.resolveSizeAndState(height, heightMeasureSpec, 0))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        for ((idx, view) in views.values.withIndex()) {
            val offsetOnLongAxis = idx * voteIconSize + idx * spaceSize

            if (orientationIsVertical) {
                view.layout(
                        paddingLeft,
                        offsetOnLongAxis + paddingTop,
                        (right - left) - paddingRight,
                        offsetOnLongAxis + paddingTop + voteIconSize)
            } else {
                view.layout(
                        offsetOnLongAxis + paddingLeft,
                        paddingTop,
                        offsetOnLongAxis + paddingLeft + voteIconSize,
                        (bottom - top) - paddingBottom)
            }
        }
    }

    private fun iconOf(icon: VoteIcon): Drawable {
        val color = when (icon.color) {
            VoteIconColor.NEUTRAL -> defaultColor
            VoteIconColor.MARKED_UP -> markedColorUp
            VoteIconColor.MARKED_DOWN -> markedColorDown
            VoteIconColor.INACTIVE -> defaultColor
        }

        return drawableGet(icon.iconId, color)
    }

    private fun triggerVoteClicked(vote: Vote) {
        if (voteState === vote) {
            doVote(iconsDef.getValue(vote).nextVote)
        } else {
            doVote(vote)
        }
    }

    /**
     * Performs a "vote" by calling the listener and updating the internal state accordingly
     */
    private fun doVote(vote: Vote, force: Boolean = false) {
        if (voteState === vote)
            return

        // check with listener, if we really want to do the vote.
        if (!force && !onVote(vote))
            return

        setVoteState(vote)
    }

    /**
     * Updates the vote state internally, does not trigger a call to the listener.
     */
    fun setVoteState(vote: Vote, animate: Boolean = true) {
        if (voteState === vote)
            return

        // set new voting state
        voteState = vote

        updateVoteViewState(animate, vote)
    }

    private fun updateVoteViewState(animated: Boolean, currentVote: Vote) {
        views.forEach { (viewVote, view) ->
            val def = iconsDef.getValue(viewVote)
            val icon = def.icons.getValue(currentVote)

            // set the correct drawable
            view.setImageDrawable(iconOf(icon))

            updateViewState(view, animated, icon.rotated,
                    alpha = icon.color === VoteIconColor.INACTIVE)
        }
    }

    private fun drawableGet(drawableId: Int, color: Int): Drawable {
        return DrawableCache.get(context, drawableId, color)
    }

    private fun updateViewState(view: View, animated: Boolean, rotated: Boolean, alpha: Boolean) {
        val targetAlpha = if (alpha) 0.5f else 1f
        val targetRotation = if (rotated) 360.0f else 0f

        if (animated) {

            // no need to animate if nothing to do
            if (!wasAnimated && view.rotation == targetRotation && view.alpha == targetAlpha)
                return

            // okay, actually start animating
            wasAnimated = true
            view.animate().rotation(targetRotation).alpha(targetAlpha).setDuration(500L).start()

        } else {
            if (wasAnimated) {
                // maybe there is some animation still running
                view.animate().cancel()
            }

            if (view.alpha != targetAlpha) {
                view.alpha = targetAlpha
            }

            if (view.rotation != targetRotation) {
                view.rotation = targetRotation
            }
        }
    }
}
