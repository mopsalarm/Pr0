package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.text.style.ImageSpan
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.menuPopupHelper
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.FollowState
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.DrawableCache
import com.pr0gramm.app.ui.base.whileIsAttachedScope
import com.pr0gramm.app.ui.base.withErrorDialog
import com.pr0gramm.app.ui.configureNewStyle
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.injector
import kotterknife.bindView
import kotlin.math.min


/**
 */
class InfoLineView(context: Context) : LinearLayout(context) {
    private val captionView: TextView by bindView(R.id.date)
    private val usernameView: UsernameView by bindView(R.id.username)
    private val voteView: VoteView by bindView(R.id.voting)
    private val followStateView: ImageView by bindView(R.id.action_follow)
    private val collectionView: CollectView by bindView(R.id.collect)

    private val drawableCache = DrawableCache()

    private val admin: Boolean = !isInEditMode && context.injector.instance<UserService>().userIsAdmin

    private var feedItem: FeedItem? = null
    private var isSelfPost: Boolean = false

    var onDetailClickedListener: PostActions? = null

    init {
        orientation = VERTICAL

        inflate(context, R.layout.post_info_line, this)

        voteView.onVote = { newVote ->
            val changed = onDetailClickedListener?.votePostClicked(newVote) ?: false
            if (changed) {
                updateViewState(newVote)
            }

            changed
        }

        followStateView.setOnClickListener {
            val iconColor = context.getStyledColor(android.R.attr.textColorSecondary)

            val popup = PopupMenu(context, followStateView)
            popup.menuPopupHelper.setForceShowIcon(true)
            popup.inflate(R.menu.menu_follow)
            popup.setOnMenuItemClickListener { followMenuClicked(it.itemId); true }

            popup.menu.findItem(R.id.action_follow_off)?.icon = drawableCache
                    .get(R.drawable.ic_action_follow_off, iconColor)

            popup.menu.findItem(R.id.action_follow_normal)?.icon = drawableCache
                    .get(R.drawable.ic_action_follow_normal, iconColor)

            popup.menu.findItem(R.id.action_follow_full)?.icon = drawableCache
                    .get(R.drawable.ic_action_follow_full, iconColor)

            popup.show()
        }

        captionView.setOnLongClickListener { _ ->
            val item = this.feedItem
            if (item != null && scoreIsVisible()) {
                val text = "${item.up} Blussies, ${item.down} Minus"

                Snackbar.make(this, text, Snackbar.LENGTH_SHORT)
                        .configureNewStyle()
                        .setAction(R.string.okay) {}
                        .show()
            }

            true
        }
    }

    /**
     * Displays the given [com.pr0gramm.app.feed.FeedItem] along with
     * the given vote.

     * @param item The item to display
     * *
     * @param vote The vote that belongs to the given item.
     */
    fun setFeedItem(item: FeedItem, isSelfPost: Boolean, vote: Vote) {
        this.feedItem = item
        this.isSelfPost = isSelfPost

        // update the views!
        usernameView.setUsername(item.user, item.mark)

        usernameView.setOnClickListener {
            onDetailClickedListener?.onUserClicked(item.user)
        }

        voteView.setVoteState(vote, animate = false)
        voteView.toggleFavIconVisibility(!isSelfPost)

        collectionView.itemId = item.id

        updateViewState(vote)
    }

    private fun formatScore(vote: Vote): String? {
        val feedItem = this.feedItem ?: return null

        return if (scoreIsVisible()) {
            val rating = feedItem.up - feedItem.down + min(1, vote.voteValue)
            rating.toString()
        } else {
            null
        }
    }

    private fun scoreIsVisible(): Boolean {
        return isOneHourOld || isSelfPost || admin
    }

    /**
     * Updates the rating using the currently set feed item and the given vote.

     * @param vote The vote that is currently selected.
     */
    @SuppressLint("SetTextI18n")
    private fun updateViewState(vote: Vote) {
        val feedItem = this.feedItem ?: return

        val viewVisibility = if (feedItem.deleted) View.INVISIBLE else View.VISIBLE

        // also hide the vote view.
        voteView.visibility = viewVisibility

        val textColor = captionView.currentTextColor

        val textSize = captionView.textSize.toInt()
        val offset = context.dp(2)

        val dClock = drawableCache.get(R.drawable.ic_clock, textColor).withInsets(bottom = offset)
        dClock.setBounds(0, 0, textSize, textSize + offset)

        val dPlus = drawableCache.get(R.drawable.ic_plus, textColor).withInsets(bottom = offset)
        dPlus.setBounds(0, 0, textSize, textSize + offset)

        ViewUpdater.replaceText(captionView, feedItem.created) {
            val date = DurationFormat.timeSincePastPointInTime(context, feedItem.created, short = true)
            val score = formatScore(vote)

            val thinsp = "\u2009"

            buildSpannedString {
                if (score != null) {
                    inSpans(ImageSpan(dPlus, ImageSpan.ALIGN_BOTTOM)) {
                        append(" ")
                    }
                    append(thinsp)
                    append(score)
                    append(" Benis")

                    append("   ")
                }

                inSpans(ImageSpan(dClock, ImageSpan.ALIGN_BOTTOM)) {
                    append(" ")
                }
                append(thinsp)
                append(date)

            }
        }
    }

    private fun followMenuClicked(selectedItemId: Int) {
        val state = when (selectedItemId) {
            R.id.action_follow_normal -> FollowState.FOLLOW
            R.id.action_follow_full -> FollowState.SUBSCRIBED
            else -> FollowState.NONE
        }
        followStateView.animate()
                .rotationYBy(360f)
                .setDuration(500L)
                .start()

        // update view
        updateFollowState(state)

        whileIsAttachedScope {
            withErrorDialog {
                // publish follow state to backend
                onDetailClickedListener?.updateFollowUser(state)
            }
        }

        // show a small hint that this is only viewable with pr0mium
        val userService: UserService = context.injector.instance()

        if (state != FollowState.NONE && !userService.canViewCategoryStalk) {
            Snackbar.make(this@InfoLineView, R.string.hint_follow_premium_only, Snackbar.LENGTH_SHORT)
                    .configureNewStyle()
                    .show()
        }
    }

    fun updateFollowState(followState: FollowState?) {
        if (followState == null) {
            followStateView.isVisible = false
            return
        }

        followStateView.isVisible = true

        when {
            followState.subscribed -> {
                val color = context.getColorCompat(ThemeHelper.accentColor)
                followStateView.setImageDrawable(drawableCache.get(R.drawable.ic_action_follow_full, color))
            }

            followState.following -> {
                val color = context.getColorCompat(ThemeHelper.accentColor)
                followStateView.setImageDrawable(drawableCache.get(R.drawable.ic_action_follow_normal, color))
            }

            else -> {
                val color = context.getStyledColor(android.R.attr.textColorSecondary)
                followStateView.setImageDrawable(drawableCache.get(R.drawable.ic_action_follow_off, color))
            }
        }
    }

    private val isOneHourOld: Boolean
        get() {
            val oneHourAgo = Instant.now() - Duration.hours(1)
            return feedItem?.created?.isBefore(oneHourAgo) ?: false
        }
}
