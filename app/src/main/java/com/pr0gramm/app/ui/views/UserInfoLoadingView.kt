package com.pr0gramm.app.ui.views

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.pr0gramm.app.R
import com.pr0gramm.app.UserClasses
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.getColorCompat
import com.pr0gramm.app.util.visible

class UserInfoLoadingView(context: Context) : FrameLayout(context) {
    private val usernameView: UsernameView
    private val usermarkView: TextView

    init {
        View.inflate(context, R.layout.user_info_loading, this)
        usernameView = find(R.id.username)
        usermarkView = find(R.id.user_type_name)

        find<View>(R.id.kpi_benis).visible = false
        find<View>(R.id.busy_indicator).visible = true
    }

    fun update(name: String, mark: Int) {
        usernameView.setUsername(name, mark)

        UserClasses.MarkStrings.getOrNull(mark)?.let {
            usermarkView.text = context.getString(it)
        }

        UserClasses.MarkColors.getOrNull(mark)?.let {
            usermarkView.setTextColor(context.getColorCompat(it))
        }
    }
}