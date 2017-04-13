package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api.Info
import com.pr0gramm.app.ui.LoginActivity
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.visible
import kotterknife.bindView
import net.danlew.android.joda.DateUtils
import org.joda.time.Duration
import org.joda.time.Instant

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
    private val messages: View by bindView(R.id.action_new_message)
    private val extraInfo: TextView by bindView(R.id.user_extra_info)

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
        messages.setOnClickListener {
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
            val registered = DateUtils.getRelativeTimeSpanString(context, user.registered, false)
            extraInfo.text = context.getString(R.string.user_registered, registered)
        }
    }

    var writeMessageEnabled: Boolean
        get() = messages.visibility == View.VISIBLE
        set(enabled) {
            messages.visible = enabled
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
}
