package com.pr0gramm.app.ui.views

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.pr0gramm.app.R
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.ThemeHelper.accentColor
import com.pr0gramm.app.util.dip2px
import com.pr0gramm.app.util.use

/**
 * A plus and a minus sign to handle votes.
 */
class VoteView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {
    private val viewRateUp: AppCompatImageView
    private val viewRateDown: AppCompatImageView

    private var voteState: Vote = Vote.NEUTRAL

    var markedColor: ColorStateList
    var markedColorDown: ColorStateList
    var defaultColor: ColorStateList

    var onVote: (Vote) -> Boolean = { false }

    val vote: Vote get() = voteState

    init {
        var orientation = 0
        var textSize = 24
        var spacing = context.dip2px(4)

        markedColor = ColorStateList.valueOf(ContextCompat.getColor(context, accentColor))
        markedColorDown = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white))
        defaultColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white))

        if (attrs != null) {
            context.theme.obtainStyledAttributes(attrs, R.styleable.VoteView, 0, 0).use { a ->
                orientation = a.getInteger(R.styleable.VoteView_orientation, orientation)
                textSize = a.getDimensionPixelSize(R.styleable.VoteView_textSize, textSize)
                spacing = a.getDimensionPixelSize(R.styleable.VoteView_spacing, spacing)

                a.getColorStateList(R.styleable.VoteView_markedColor)?.let { markedColor = it }
                a.getColorStateList(R.styleable.VoteView_markedColorDown)?.let { markedColorDown = it }
                a.getColorStateList(R.styleable.VoteView_defaultColor)?.let { defaultColor = it }
            }
        }

        this.gravity = Gravity.CENTER
        this.orientation = if (orientation == 1) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL

        viewRateUp = AppCompatImageView(context)
        viewRateUp.layoutParams = ViewGroup.LayoutParams(textSize, textSize)
        viewRateUp.setImageResource(R.drawable.ic_vote_up)
        viewRateUp.setImageTintCompat(defaultColor)

        viewRateDown = AppCompatImageView(context)
        viewRateDown.setImageResource(R.drawable.ic_vote_down)
        viewRateDown.setImageTintCompat(defaultColor)

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
        val duration = if (animated) 500 else 0

        if (voteState === Vote.NEUTRAL) {
            viewRateUp.setImageTintCompat(defaultColor)
            viewRateDown.setImageTintCompat(defaultColor)
            viewRateUp.animate().rotation(0f).alpha(1f).setDuration(duration.toLong()).start()
            viewRateDown.animate().rotation(0f).alpha(1f).setDuration(duration.toLong()).start()
        }

        if (voteState === Vote.UP || voteState === Vote.FAVORITE) {
            viewRateUp.setImageTintCompat(markedColor)
            viewRateDown.setImageTintCompat(defaultColor)
            viewRateUp.animate().rotation(360f).alpha(1f).setDuration(duration.toLong()).start()
            viewRateDown.animate().rotation(0f).alpha(0.5f).setDuration(duration.toLong()).start()
        }

        if (voteState === Vote.DOWN) {
            viewRateUp.setImageTintCompat(defaultColor)
            viewRateDown.setImageTintCompat(markedColorDown)
            viewRateUp.animate().rotation(0f).alpha(0.5f).setDuration(duration.toLong()).start()
            viewRateDown.animate().rotation(360f).alpha(1f).setDuration(duration.toLong()).start()
        }
    }

    private fun AppCompatImageView.setImageTintCompat(color: ColorStateList) {
        ImageViewCompat.setImageTintList(this, color)
    }
}
