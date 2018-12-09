package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.UserClassesService
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.util.*
import com.squareup.picasso.Picasso
import org.kodein.di.direct
import org.kodein.di.erased.instance
import java.util.concurrent.TimeUnit

/**
 */
@SuppressLint("ViewConstructor")
class UserInfoView(context: Context, private val userActionListener: UserActionListener) : FrameLayout(context) {
    init {
        View.inflate(context, R.layout.user_info_cell_v2, this)
    }

    private val username: UsernameView = find(R.id.username)
    private val benis: TextView = find(R.id.kpi_benis)
    private val favorites: TextView = find(R.id.kpi_favorites)
    private val comments: TextView = find(R.id.kpi_comments)
    private val tags: TextView = find(R.id.kpi_tags)
    private val uploads: TextView = find(R.id.kpi_uploads)
    private val extraInfo: TextView = find(R.id.user_extra_info)
    private val writeNewMessage: View = find(R.id.action_new_message)
    private val writeNewMessageTitle: View = find(R.id.action_new_message_title)
    private val badgesContainer: RecyclerView = find(R.id.badges_container)
    private val userTypeName: TextView = find(R.id.user_type_name)
    private val showCommentsContainer: View = comments.parent as View

    private val userClassesService = kodein.direct.instance<UserClassesService>()

    fun updateUserInfo(info: Api.Info, comments: List<Api.UserComments.UserComment>, myself: Boolean) {
        // user info
        val user = info.user
        username.setUsername(user.name, user.mark)
        benis.text = user.score.toString()

        // counts
        tags.text = info.tagCount.toString()
        uploads.text = info.uploadCount.toString()
        this.comments.text = info.commentCount.toString()

        val userClass = userClassesService.get(user.mark)
        userTypeName.setTextColor(userClass.color)
        userTypeName.text = userClass.name

        showCommentsContainer.visible = comments.isNotEmpty()

        writeNewMessage.visible = !myself
        writeNewMessageTitle.visible = !myself

        // open message dialog for user
        writeNewMessage.setOnClickListener {
            userActionListener.onWriteMessageClicked(user.name)
        }

        (this.comments.parent as View).setOnClickListener {
            userActionListener.onShowCommentsClicked()
        }

        (uploads.parent as View).setOnClickListener {
            userActionListener.onShowUploadsClicked(user.name)
        }

        if (info.likesArePublic && info.likeCount > 0) {
            favorites.text = info.likeCount.toString()

            (favorites.parent as View).setOnClickListener {
                userActionListener.onUserFavoritesClicked(user.name)
            }
        } else {
            // remove the view
            (favorites.parent as View).visibility = View.GONE
        }

        val badges = mutableListOf<BadgeInfo>()
        // add badge for "x comments"
        (info.commentCount / 1000).takeIf { it > 0 }?.let {
            badges += BadgeInfo(
                    "comments.png",
                    context.getString(R.string.badge_comments, it.toString()),
                    text = "${it}k", textColor = Color.BLACK)
        }

        if (info.user.itemDeleteCount > 0) {
            val count = info.user.itemDeleteCount
            badges += BadgeInfo(
                    "itemdelete.png",
                    context.getString(R.string.badge_deleted_items, count),
                    text = "$count", textColor = Color.WHITE)
        }

        if (info.user.commentDeleteCount > 0) {
            val count = info.user.commentDeleteCount
            badges += BadgeInfo(
                    "commentdelete.png",
                    context.getString(R.string.badge_deleted_comments, count),
                    text = "$count", textColor = Color.WHITE)
        }

        // add badge for "x years on pr0gramm"
        val years = Duration.between(Instant.now(), info.user.registered).convertTo(TimeUnit.DAYS) / 365
        if (years > 0) {
            badges += BadgeInfo(
                    "years.png",
                    context.getString(R.string.badge_time, years.toString()),
                    text = years.toString(), textColor = Color.WHITE)
        }

        info.badges.mapTo(badges) { badge ->
            BadgeInfo(badge.image, badge.description ?: "")
        }

        badgesContainer.adapter = BadgeAdapter(badges)
        badgesContainer.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        badgesContainer.isHorizontalFadingEdgeEnabled = true
        badgesContainer.setFadingEdgeLength(AndroidUtility.dp(context, 24))

        if (badges.isNotEmpty()) {
            // scroll to the end after a short delay.
            val r = { badgesContainer.smoothScrollToPosition(badges.size - 1) }
            badgesContainer.postDelayed(r, 500)
        }

        // info about banned/register date
        if (user.banned && !user.inactive) {
            val bannedUntil = user.bannedUntil
            if (bannedUntil == null) {
                extraInfo.setText(R.string.user_banned_forever)
            } else {
                val durationStr = DurationFormat.timeSpan(context, bannedUntil, short = false)
                extraInfo.text = context.getString(R.string.user_banned, durationStr)
            }
        } else {
            val dateStr = DurationFormat.timeToPointInTime(context, user.registered, short = false)
            val resId = if (user.inactive) R.string.user_registered_inactive else R.string.user_registered
            extraInfo.text = context.getString(resId, dateStr)
        }
    }

    interface UserActionListener {
        fun onWriteMessageClicked(name: String)
        fun onUserFavoritesClicked(name: String)
        fun onShowCommentsClicked()
        fun onShowUploadsClicked(name: String)
    }

    private data class BadgeInfo(val image: String, val description: String,
                                 val text: String? = null,
                                 val textColor: Int = Color.WHITE)

    private class BadgeAdapter(val badges: List<BadgeInfo>) : androidx.recyclerview.widget.RecyclerView.Adapter<BadgeViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, idx: Int): BadgeViewHolder {
            val view = parent.layoutInflater.inflate(R.layout.badge, parent, false)
            return BadgeViewHolder(view)
        }

        override fun onBindViewHolder(holder: BadgeViewHolder, idx: Int) {
            holder.set(badges[idx], idx == 0)
        }

        override fun getItemCount(): Int {
            return badges.size
        }
    }

    private class BadgeViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        private val imageView: ImageView = view.find(R.id.image)
        private val textView: TextView = view.find(R.id.text)

        fun set(badge: BadgeInfo, firstItem: Boolean) {
            if (badge.text == null) {
                textView.visible = false
            } else {
                textView.visible = true
                textView.text = badge.text
                textView.setTextColor(badge.textColor)
            }

            itemView.setOnClickListener {
                Toast.makeText(itemView.context, badge.description, Toast.LENGTH_SHORT).show()
            }

            if (!itemView.isInEditMode) {
                val context = itemView.context
                val picasso = context.directKodein.instance<Picasso>()

                val localImageId = knownImages[badge.image]
                if (localImageId != null) {
                    imageView.setImageResource(localImageId)
                } else {
                    val url = UriHelper.of(context).badgeImageUrl(badge.image)
                    picasso.load(url).into(imageView)
                }
            }

            // hack, if it is the first item, we need to add 12dp of left margin
            val margin = if (firstItem) AndroidUtility.dp(itemView.context, 12) else 0
            (itemView.layoutParams as? MarginLayoutParams)?.leftMargin = margin
        }

        companion object {
            private val knownImages = mapOf(
                    "years.png" to R.drawable.badge_years,
                    "comments.png" to R.drawable.badge_comments,
                    "social-share.png" to R.drawable.badge_social,
                    "secret-santa-2014.png" to R.drawable.badge_secret_santa,
                    "benitrat0r-lose.png" to R.drawable.badge_benitrator_lose,
                    "benitrat0r-win.png" to R.drawable.badge_benitrator_win,
                    "contract.png" to R.drawable.badge_contract,
                    "connect4-red.png" to R.drawable.badge_connect4_red,
                    "connect4-blue.png" to R.drawable.badge_connect4_blue,
                    "krebs-donation.png" to R.drawable.badge_krebs,
                    "itemdelete.png" to R.drawable.deleted_item,
                    "commentdelete.png" to R.drawable.deleted_comment,
                    "art13.png" to R.drawable.badge_art13
            )
        }
    }

}
