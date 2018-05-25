package com.pr0gramm.app.ui.views

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api

typealias OnUserClickedListener = (id: Int, name: String) -> Unit

/**
 */
class UserHintView(context: Context) : FrameLayout(context) {
    private val uploads: View
    private val username: UsernameView

    init {
        View.inflate(context, R.layout.user_uploads_link, this)
        uploads = findViewById(R.id.uploads)
        username = findViewById(R.id.username)
    }

    fun update(user: Api.Info.User, onClick: OnUserClickedListener) {
        username.setUsername(user.name, user.mark)
        uploads.setOnClickListener {
            onClick(user.id, user.name)
        }
    }
}
