package com.pr0gramm.app.ui.views

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.pr0gramm.app.R
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.ThemeHelper.accentColor
import com.pr0gramm.app.util.use
import kotlin.properties.Delegates.notNull

/**
 * A plus and a minus sign to handle votes.
 */
class VoteView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {
    private val viewRateUp: Pr0grammIconView
    private val viewRateDown: Pr0grammIconView

    private var voteState: Vote = Vote.NEUTRAL

    var markedColor: ColorStateList by notNull()
    var markedColorDown: ColorStateList by notNull()
    var defaultColor: ColorStateList by notNull()
    var onVote: (Vote) -> Boolean = { false }

    val vote: Vote get() = voteState

    init {
        var orientation = 0
        var spacing = 0
        var textSize = 24

        markedColor = ColorStateList.valueOf(ContextCompat.getColor(context, accentColor))
        markedColorDown = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white))
        defaultColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white))

        if (attrs != null) {
            context.theme.obtainStyledAttributes(attrs, R.styleable.VoteView, 0, 0).use { a ->
                orientation = a.getInteger(R.styleable.VoteView_orientation, orientation)
                spacing = a.getDimensionPixelOffset(R.styleable.VoteView_spacing, spacing)
                textSize = a.getDimensionPixelSize(R.styleable.VoteView_textSize, textSize)

                a.getColorStateList(R.styleable.VoteView_markedColor)?.let { markedColor = it }
                a.getColorStateList(R.styleable.VoteView_markedColorDown)?.let { markedColorDown = it }
                a.getColorStateList(R.styleable.VoteView_defaultColor)?.let { defaultColor = it }
            }
        }

        setOrientation(if (orientation == 1) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL)

        viewRateUp = newVoteButton(context, textSize, "+")
        viewRateDown = newVoteButton(context, textSize, "-")

        // add views
        addView(viewRateUp)
        addView(viewRateDown)

        // add padding between the views
        if (spacing > 0) {
            val view = View(context)
            view.layoutParams = ViewGroup.LayoutParams(spacing, spacing)
            addView(view, 1)
        }

        // set initial voting state
        setVoteState(Vote.NEUTRAL, true)

        // register listeners
        viewRateUp.setOnClickListener { triggerUpVoteClicked() }
        viewRateDown.setOnClickListener { triggerDownVoteClicked() }
    }

    private fun newVoteButton(context: Context, textSize: Int, text: String): Pr0grammIconView {
        val view = Pr0grammIconView(context)
        view.text = text
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        view.setTextColor(defaultColor)
        view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)

        return view
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
            viewRateUp.setTextColor(defaultColor)
            viewRateDown.setTextColor(defaultColor)
            viewRateUp.animate().rotation(0f).alpha(1f).setDuration(duration.toLong()).start()
            viewRateDown.animate().rotation(0f).alpha(1f).setDuration(duration.toLong()).start()
        }

        if (voteState === Vote.UP || voteState === Vote.FAVORITE) {
            viewRateUp.setTextColor(markedColor)
            viewRateDown.setTextColor(defaultColor)
            viewRateUp.animate().rotation(360f).alpha(1f).setDuration(duration.toLong()).start()
            viewRateDown.animate().rotation(0f).alpha(0.5f).setDuration(duration.toLong()).start()
        }

        if (voteState === Vote.DOWN) {
            viewRateUp.setTextColor(defaultColor)
            viewRateDown.setTextColor(markedColorDown)
            viewRateUp.animate().rotation(0f).alpha(0.5f).setDuration(duration.toLong()).start()
            viewRateDown.animate().rotation(360f).alpha(1f).setDuration(duration.toLong()).start()
        }
    }
}
