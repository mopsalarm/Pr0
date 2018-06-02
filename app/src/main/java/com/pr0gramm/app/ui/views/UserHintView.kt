package com.pr0gramm.app.ui.views

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.pr0gramm.app.R

typealias OnUserClickedListener = (name: String) -> Unit

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

    fun update(name: String, mark: Int, onClick: OnUserClickedListener) {
        username.setUsername(name, mark)

        uploads.setOnClickListener {
            onClick(name)
        }
    }
}
