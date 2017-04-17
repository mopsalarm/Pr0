package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import kotterknife.bindView

/**
 */
@SuppressLint("ViewConstructor")
class UserInfoFoundView(context: Context, userInfo: Api.Info) : FrameLayout(context) {
    private val uploads: View by bindView(R.id.uploads)
    private val username: UsernameView by bindView(R.id.username)

    var uploadsClickedListener: (user: Int, name: String) -> Unit = { user, name -> }

    init {
        View.inflate(context, R.layout.user_uploads_link, this)
        update(userInfo)
    }

    fun update(info: Api.Info) {
        // user info
        val user = info.user
        username.setUsername(user.name, user.mark)

        uploads.setOnClickListener {
            uploadsClickedListener(user.id, user.name)
        }
    }

    interface OnUserClickedListener {
        fun onClicked(userId: Int, name: String)
    }
}
