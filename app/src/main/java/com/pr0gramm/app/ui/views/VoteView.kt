package com.pr0gramm.app.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import com.pr0gramm.app.R
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.DrawableCache
import com.pr0gramm.app.util.dip2px
import com.pr0gramm.app.util.getColorCompat
import com.pr0gramm.app.util.use

/**
 * A plus and a minus sign to handle votes.
 */
class VoteView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {
    private val viewRateUp: AppCompatImageView
    private val viewRateDown: AppCompatImageView

    private var voteState: Vote = Vote.NEUTRAL

    var markedColor: Int
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

        context.theme.obtainStyledAttributes(attrs, R.styleable.VoteView, 0, 0).use { a ->
            orientation = a.getInteger(R.styleable.VoteView_orientation, orientation)
            textSize = a.getDimensionPixelSize(R.styleable.VoteView_textSize, textSize)
            spacing = a.getDimensionPixelSize(R.styleable.VoteView_spacing, spacing)

            markedColor = a.getColor(R.styleable.VoteView_markedColor, context.getColorCompat(ThemeHelper.accentColor))
            markedColorDown = a.getColor(R.styleable.VoteView_markedColorDown, context.getColorCompat(R.color.white))
            defaultColor = a.getColor(R.styleable.VoteView_defaultColor, context.getColorCompat(R.color.white))
        }

        this.gravity = Gravity.CENTER
        this.orientation = if (orientation == 1) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL

        viewRateUp = AppCompatImageView(context)
        viewRateUp.layoutParams = ViewGroup.LayoutParams(textSize, textSize)
        viewRateUp.setImageDrawable(drawableCache.get(R.drawable.ic_vote_up, defaultColor))

        viewRateDown = AppCompatImageView(context)
        viewRateDown.setImageDrawable(drawableCache.get(R.drawable.ic_vote_down, defaultColor))

        viewRateDown.layoutParams = MarginLayoutParams(textSize, textSize).apply {
            if (orientation == VERTICAL) {
                topMargin = spacing
            } else {
                leftMargin = spacing
            }
        }

        // add views
        addView(viewRateUp)
        addView(viewRateDown)

        // set initial voting state
        setVoteState(Vote.NEUTRAL, true)

        // register listeners
        viewRateUp.setOnClickListener { triggerUpVoteClicked() }
        viewRateDown.setOnClickListener { triggerDownVoteClicked() }
    }

    private fun triggerUpVoteClicked() = doVote(voteState.nextUpVote)
    private fun triggerDownVoteClicked() = doVote(voteState.nextDownVote)

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

        if (!isInEditMode) {
            updateVoteViewState(animate)
        }
    }

    private fun updateVoteViewState(animated: Boolean) {
        if (voteState === Vote.NEUTRAL) {
            viewRateUp.setImageDrawable(drawableCache.get(R.drawable.ic_vote_up, defaultColor))
            viewRateDown.setImageDrawable(drawableCache.get(R.drawable.ic_vote_down, defaultColor))

            updateViewState(viewRateUp, animated, rotation = 0f, alpha = 1f)
            updateViewState(viewRateDown, animated, rotation = 0f, alpha = 1f)
        }

        if (voteState === Vote.UP || voteState === Vote.FAVORITE) {
            viewRateUp.setImageDrawable(drawableCache.get(R.drawable.ic_vote_up, markedColor))
            viewRateDown.setImageDrawable(drawableCache.get(R.drawable.ic_vote_down, defaultColor))

            updateViewState(viewRateUp, animated, rotation = 360f, alpha = 1f)
            updateViewState(viewRateDown, animated, rotation = 0f, alpha = 0.5f)
        }

        if (voteState === Vote.DOWN) {
            viewRateUp.setImageDrawable(drawableCache.get(R.drawable.ic_vote_up, defaultColor))
            viewRateDown.setImageDrawable(drawableCache.get(R.drawable.ic_vote_down, markedColorDown))

            updateViewState(viewRateUp, animated, rotation = 0f, alpha = 0.5f)
            updateViewState(viewRateDown, animated, rotation = 360f, alpha = 1f)
        }
    }

    private fun updateViewState(view: View, animated: Boolean, rotation: Float, alpha: Float) {
        if (animated) {

            // no need to animate if nothing to do
            if (!wasAnimated && view.rotation == rotation && view.alpha == alpha)
                return

            // okay, actually start animating
            wasAnimated = true
            view.animate().rotation(rotation).alpha(alpha).setDuration(500L).start()

        } else {
            if (wasAnimated) {
                // maybe there is some animation still running
                view.animate().cancel()
            }

            if (view.alpha != alpha) {
                view.alpha = alpha
            }

            if (view.rotation != rotation) {
                view.rotation = rotation
            }
        }
    }

    companion object {
        private val drawableCache = DrawableCache()
    }
}
