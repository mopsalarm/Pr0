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
import com.jakewharton.rxbinding.view.clicks
import com.pr0gramm.app.R
import com.pr0gramm.app.UserClasses
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.util.*
import com.squareup.picasso.Picasso
import org.joda.time.Instant
import org.joda.time.Years

/**
 */
@SuppressLint("ViewConstructor")
class UserInfoView(context: Context, private val userActionListener: UserActionListener) : FrameLayout(context) {
    private val username: UsernameView
    private val benis: TextView
    private val favorites: TextView
    private val comments: TextView
    private val tags: TextView
    private val uploads: TextView
    private val extraInfo: TextView
    private val writeNewMessage: View
    private val writeNewMessageContainer: View
    private val badgesContainer: ViewGroup
    private val userTypeName: TextView
    private val showCommentsContainer: View

    init {
        View.inflate(context, R.layout.user_info_cell_v2, this)
        username = findViewById(R.id.username)
        benis = findViewById(R.id.kpi_benis)
        favorites = findViewById(R.id.kpi_favorites)
        comments = findViewById(R.id.kpi_comments)
        tags = findViewById(R.id.kpi_tags)
        uploads = findViewById(R.id.kpi_uploads)
        extraInfo = findViewById(R.id.user_extra_info)
        writeNewMessage = findViewById(R.id.action_new_message)
        writeNewMessageContainer = findViewById(R.id.action_new_message_container)
        badgesContainer = findViewById(R.id.badges_container)
        userTypeName = findViewById(R.id.user_type_name)

        showCommentsContainer = comments.parent as View
    }

    fun updateUserInfo(info: Api.Info, comments: List<Api.UserComments.UserComment>, myself: Boolean) {
        // user info
        val user = info.user
        username.setUsername(user.name, user.mark)
        benis.text = user.score.toString()

        // counts
        tags.text = info.tagCount.toString()
        uploads.text = info.uploadCount.toString()
        this.comments.text = info.commentCount.toString()

        userTypeName.setTextColor(context.getColorCompat(UserClasses.MarkColors[user.mark]))
        userTypeName.text = context.getString(UserClasses.MarkStrings[user.mark]).toUpperCase()

        showCommentsContainer.visible = comments.isNotEmpty()

        writeNewMessage.visible = !myself
        writeNewMessageContainer.visible = !myself

        // open message dialog for user
        writeNewMessage.setOnClickListener {
            userActionListener.onWriteMessageClicked(user.id, user.name)
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
        
        badgesContainer.removeAllViews()

        // add badge for "x comments"
        (info.commentCount / 1000).takeIf { it > 0 }?.let {
            appendBadgeView(
                    "comments.png",
                    context.getString(R.string.badge_comments, it.toString()),
                    text = "${it}k",
                    textColor = Color.BLACK)
        }

        // add badge for "x years on pr0gramm"
        Years.yearsBetween(info.user.registered, Instant.now()).years.takeIf { it > 0 }?.let {
            appendBadgeView(
                    "years.png",
                    context.getString(R.string.badge_time, it.toString()),
                    text = it.toString())
        }

        info.badges.forEach { badge ->
            appendBadgeView(badge.image, badge.description ?: "")
        }

        // info about banned/register date
        if (user.banned != 0) {
            val bannedUntil = user.bannedUntil
            if (bannedUntil == null) {
                extraInfo.setText(R.string.user_banned_forever)
            } else {
                val durationStr = formatTimeTo(context, bannedUntil, TimeMode.DURATION)
                extraInfo.text = context.getString(R.string.user_banned, durationStr)
            }
        } else {
            val relativeStr = formatTimeTo(context, user.registered, TimeMode.SINCE)
            extraInfo.text = context.getString(R.string.user_registered, relativeStr)
        }
    }

    /**
     * Deflates and composes a badge view and adds that view to the
     * actionsContainer.
     */
    private fun appendBadgeView(image: String, description: String,
                                text: String? = null,
                                textColor: Int? = null) {

        val view = layoutInflater.inflate(R.layout.badge, badgesContainer, false)

        view.clicks().subscribe {
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
        badgesContainer.addView(view)
    }

    interface UserActionListener {
        fun onWriteMessageClicked(userId: Int, name: String)
        fun onUserFavoritesClicked(name: String)
        fun onShowCommentsClicked()
        fun onShowUploadsClicked(name: String)
    }

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
            "krebs-donation.png" to R.drawable.badge_krebs)
}
