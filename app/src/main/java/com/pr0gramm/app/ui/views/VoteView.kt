package com.pr0gramm.app.ui.views

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import com.pr0gramm.app.R
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.DrawableCache
import com.pr0gramm.app.util.dip2px
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
                Vote.DOWN to VoteIcon(R.drawable.ic_vote_up, VoteIconColor.INACTIVE),
                Vote.FAVORITE to VoteIcon(R.drawable.ic_vote_up, VoteIconColor.MARKED_UP, rotated = true)
        ),

        Vote.DOWN to VoteState(Vote.NEUTRAL,
                Vote.NEUTRAL to VoteIcon(R.drawable.ic_vote_down, VoteIconColor.NEUTRAL),
                Vote.UP to VoteIcon(R.drawable.ic_vote_down, VoteIconColor.INACTIVE),
                Vote.DOWN to VoteIcon(R.drawable.ic_vote_down, VoteIconColor.MARKED_DOWN, rotated = true),
                Vote.FAVORITE to VoteIcon(R.drawable.ic_vote_down, VoteIconColor.INACTIVE)
        ),

        Vote.FAVORITE to VoteState(Vote.UP,
                Vote.NEUTRAL to VoteIcon(R.drawable.ic_vote_fav_outline, VoteIconColor.NEUTRAL),
                Vote.UP to VoteIcon(R.drawable.ic_vote_fav_outline, VoteIconColor.INACTIVE),
                Vote.DOWN to VoteIcon(R.drawable.ic_vote_fav_outline, VoteIconColor.INACTIVE),
                Vote.FAVORITE to VoteIcon(R.drawable.ic_vote_fav, VoteIconColor.MARKED_UP)
        ))

/**
 * A plus and a minus sign to handle votes.
 */
class VoteView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {
    private val drawableCache = DrawableCache()
    private val views: Map<Vote, AppCompatImageView>

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
        var orientation = 0
        var textSize = 24
        var spacing = context.dip2px(4)

        val fav: Boolean

        context.theme.obtainStyledAttributes(attrs, R.styleable.VoteView, 0, 0).use { a ->
            orientation = a.getInteger(R.styleable.VoteView_orientation, orientation)
            textSize = a.getDimensionPixelSize(R.styleable.VoteView_textSize, textSize)
            spacing = a.getDimensionPixelSize(R.styleable.VoteView_spacing, spacing)

            markedColorUp = a.getColor(R.styleable.VoteView_markedColor, context.getColorCompat(ThemeHelper.accentColor))
            markedColorDown = a.getColor(R.styleable.VoteView_markedColorDown, context.getColorCompat(R.color.white))
            defaultColor = a.getColor(R.styleable.VoteView_defaultColor, context.getColorCompat(R.color.white))

            fav = a.getBoolean(R.styleable.VoteView_fav, false)
        }

        this.gravity = Gravity.CENTER
        this.orientation = if (orientation == 1) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL

        val votes = if (fav) listOf(Vote.UP, Vote.DOWN, Vote.FAVORITE) else listOf(Vote.UP, Vote.DOWN)

        // ripple effect
        val backgroundId = context.getStyledResourceId(android.R.attr.selectableItemBackgroundBorderless)

        views = votes
                .mapIndexed { idx, vote ->
                    val def = iconsDef.getValue(vote)

                    // configure spacing
                    val lp = MarginLayoutParams(textSize, textSize)
                    if (idx > 0) {
                        if (orientation == VERTICAL) {
                            lp.topMargin = spacing
                        } else {
                            lp.leftMargin = spacing
                        }
                    }

                    // create the actual view
                    val view = AppCompatImageView(context)
                    view.setBackgroundResource(backgroundId)
                    view.setImageDrawable(iconOf(def.icons.getValue(Vote.NEUTRAL)))
                    view.layoutParams = lp
                    view.setOnClickListener { triggerVoteClicked(vote) }

                    vote to view
                }
                .toMap()


        views.values.forEach { addView(it) }

        if (isInEditMode) {
            setVoteState(Vote.UP, false)
        } else {
            // set initial voting state
            setVoteState(Vote.NEUTRAL, true)
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
    fun doVote(vote: Vote, force: Boolean = false) {
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
        return drawableCache.get(context, drawableId, color)
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
