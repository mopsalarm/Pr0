package com.pr0gramm.app.ui.views

import android.content.Context
import android.view.View
import android.widget.ImageButton
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.DrawableCache
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.getColorCompat


class VoteViewController(
    private val upView: ImageButton, private val downView: ImageButton,
    private val activeScale: Float = 1.2f, private val inactiveScale: Float = 1f,
) {
    var currentVote: Vote = Vote.NEUTRAL
        private set

    private val context: Context = upView.context
    private val userService: UserService = context.injector.instance()

    // listeners for vote
    var onVoteClicked: ((vote: Vote) -> Boolean)? = null

    init {
        require(upView.parent === downView.parent) {
            "upView & downView must share the same view parent."
        }

        upView.setOnClickListener { handleVoteClicked(Vote.UP) }
        downView.setOnClickListener { handleVoteClicked(Vote.DOWN) }

        updateViewState(animate = false)
    }

    fun updateVote(vote: Vote, animate: Boolean) {
        if (currentVote != vote) {
            currentVote = vote
            updateViewState(animate)
        }
    }

    private fun handleVoteClicked(vote: Vote) {
        val nextVote = when (vote) {
            currentVote -> Vote.NEUTRAL
            else -> vote
        }

        if (onVoteClicked?.invoke(nextVote) == false) {
            // veto, do not apply vote
            return
        }

        updateVote(nextVote, animate = true)
    }

    private fun updateViewState(animate: Boolean) {
        val (upColor, downColor) = viewTintOf(currentVote)

        upView.setImageDrawable(DrawableCache.get(context, R.drawable.ic_vote_up, upColor))
        downView.setImageDrawable(DrawableCache.get(context, R.drawable.ic_vote_down, downColor))

        updateViewState(
            upView, animate && upView.isAttachedToWindow,
            scale = currentVote == Vote.UP,
            inactive = currentVote == Vote.DOWN,
        )

        updateViewState(
            downView, animate && downView.isAttachedToWindow,
            scale = currentVote == Vote.DOWN,
            inactive = currentVote == Vote.UP,
        )
    }

    private fun updateViewState(view: View, animate: Boolean, scale: Boolean, inactive: Boolean) {
        val targetAlpha = if (inactive) 0.25f else 1f
        val targetScale = if (scale) activeScale else inactiveScale
        val targetAngle = if (scale && Settings.rotateVoteView && userService.userIsPremium) 180f else 0f

        if (animate) {
            // no need to animate if nothing to do
            if (view.scaleX == targetScale && view.alpha == targetAlpha && view.rotation == targetAngle) {
                return
            }

            // okay, actually start animating
            view.animate()
                .scaleX(targetScale).scaleY(targetScale)
                .alpha(targetAlpha)
                .rotation(targetAngle)
                .setDuration(250).start()

        } else {
            // stop any animation that is still running
            view.animate().cancel()

            if (view.alpha != targetAlpha) {
                view.alpha = targetAlpha
            }

            view.scaleX = targetScale
            view.scaleY = targetScale
        }
    }

    private fun viewTintOf(currentVote: Vote): Pair<Int, Int> {
        val colorNeutral = AndroidUtility.resolveColorAttribute(context, android.R.attr.textColorSecondary)
        val colorUp = context.getColorCompat(ThemeHelper.accentColor)
        val colorDown = context.getColorCompat(R.color.white)

        return when (currentVote) {
            Vote.UP -> Pair(colorUp, colorNeutral)
            Vote.DOWN -> Pair(colorNeutral, colorDown)
            Vote.NEUTRAL -> Pair(colorNeutral, colorNeutral)
        }
    }
}
