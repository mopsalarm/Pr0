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
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import com.jakewharton.rxbinding.view.longClicks
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api.Info
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.LoginActivity
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.layoutInflater
import com.pr0gramm.app.util.removeFromParent
import com.pr0gramm.app.util.visible
import com.squareup.picasso.Picasso
import kotterknife.bindView
import net.danlew.android.joda.DateUtils
import org.joda.time.Duration
import org.joda.time.Instant
import org.joda.time.Years

/**
 */
@SuppressLint("ViewConstructor")
class UserInfoCell(context: Context, userInfo: Info,
                   private val doIfAuthorizedHelper: LoginActivity.DoIfAuthorizedHelper) : FrameLayout(context) {

    private val username: UsernameView by bindView(R.id.username)
    private val benis: TextView by bindView(R.id.kpi_benis)
    private val favorites: TextView by bindView(R.id.kpi_favorites)
    private val comments: TextView by bindView(R.id.kpi_comments)
    private val tags: TextView by bindView(R.id.kpi_tags)
    private val uploads: TextView by bindView(R.id.kpi_uploads)
    private val extraInfo: TextView by bindView(R.id.user_extra_info)
    private val writeNewMessage: View by bindView(R.id.action_new_message)
    private val actionsContainer: ViewGroup by bindView(R.id.actions_container)

    private val showComments: View

    var userActionListener: UserActionListener? = null

    init {
        View.inflate(context, R.layout.user_info_cell_v2, this)
        showComments = find<View>(R.id.kpi_comments).parent as View
        updateUserInfo(userInfo)
    }

    fun updateUserInfo(info: Info) {
        // user info
        val user = info.user
        username.setUsername(user.name, user.mark)
        benis.text = user.score.toString()

        // counts
        tags.text = info.tagCount.toString()
        uploads.text = info.uploadCount.toString()
        comments.text = info.commentCount.toString()

        // open message dialog for user
        writeNewMessage.setOnClickListener {
            if (userActionListener != null) {
                doIfAuthorizedHelper.run {
                    userActionListener?.onWriteMessageClicked(user.id, user.name)
                }
            }
        }

        (comments.parent as View).setOnClickListener {
            userActionListener?.onShowCommentsClicked()
        }

        (uploads.parent as View).setOnClickListener {
            userActionListener?.onShowUploadsClicked(user.id, user.name)
        }

        if (info.likesArePublic() && info.likeCount > 0) {
            favorites.text = info.likeCount.toString()

            (favorites.parent as View).setOnClickListener {
                userActionListener?.onUserFavoritesClicked(user.name)
            }
        } else {
            // remove the view
            (favorites.parent as View).visibility = View.GONE
        }

        // add badge for "x comments"
        (info.commentCount / 1000).takeIf { it > 0 }?.let {
            makeBadgeView(
                    "comments.png",
                    context.getString(R.string.badge_comments, it.toString()),
                    text = "${it}k",
                    textColor = Color.BLACK)
        }

        // add badge for "x years on pr0gramm"
        Years.yearsBetween(info.user.registered, Instant.now()).years.takeIf { it > 0 }?.let {
            makeBadgeView(
                    "years.png",
                    context.getString(R.string.badge_time, it.toString()),
                    text = it.toString())
        }

        info.badges.forEach { badge ->
            makeBadgeView(badge.image, badge.description ?: "")
        }

        // info about banned/register date
        if (user.isBanned != 0) {
            val bannedUntil = user.bannedUntil
            if (bannedUntil == null) {
                extraInfo.setText(R.string.user_banned_forever)
            } else {
                val duration = Duration(Instant.now(), bannedUntil)
                val durationStr = DateUtils.formatDuration(context, duration)
                extraInfo.text = context.getString(R.string.user_banned, durationStr)
            }
        } else {
            val dateStr = DateUtils.formatDateTime(context, user.registered, 0)
            val relativeStr = android.text.format.DateUtils.getRelativeDateTimeString(context,
                    user.registered.millis, 0, android.text.format.DateUtils.YEAR_IN_MILLIS * 100L, 0)

            extraInfo.text = context.getString(R.string.user_registered, dateStr, relativeStr)
        }
    }

    /**
     * Deflates and composes a badge view and adds that view to the
     * actionsContainer.
     */
    private fun makeBadgeView(image: String, description: String,
                              text: String? = null,
                              textColor: Int? = null) {

        val view = layoutInflater.inflate(R.layout.badge, actionsContainer, false)

        view.longClicks().subscribe {
            Toast.makeText(context, description, Toast.LENGTH_SHORT).show()
        }

        val textView = view.find<TextView>(R.id.text)
        if (text == null) {
            textView.removeFromParent()
        } else {
            textView.text = text
            textColor?.let { textView.setTextColor(it) }
        }

        val imageView = view.find<ImageView>(R.id.image)
        if (!isInEditMode) {
            val picasso = appKodein().instance<Picasso>()

            val localImageId = knownImages.get(image)
            if (localImageId != null) {
                imageView.setImageResource(localImageId)
            } else {
                val url = UriHelper.of(context).badgeImageUrl(image)
                picasso.load(url).into(imageView)
            }
        }

        // add the view to parent
        actionsContainer.addView(view)
    }

    var writeMessageEnabled: Boolean
        get() = writeNewMessage.visibility == View.VISIBLE
        set(enabled) {
            writeNewMessage.visible = enabled
        }

    var showCommentsEnabled: Boolean
        get() = showComments.visibility == View.VISIBLE
        set(enabled) {
            showComments.visible = enabled
        }

    interface UserActionListener {
        fun onWriteMessageClicked(userId: Int, name: String)

        fun onUserFavoritesClicked(name: String)

        fun onShowCommentsClicked()

        fun onShowUploadsClicked(id: Int, name: String)
    }

    companion object {
        private val knownImages = mapOf(
                "years.png" to R.drawable.badge_years,
                "comments.png" to R.drawable.badge_comments,
                "social-share.png" to R.drawable.badge_social,
                "secret-santa-2014.png" to R.drawable.badge_secret_santa,
                "benitrat0r-lose.png" to R.drawable.badge_benitrator_lose,
                "benitrat0r-win.png" to R.drawable.badge_benitrator_win,
                "contract.png" to R.drawable.badge_contract)

    }
}
