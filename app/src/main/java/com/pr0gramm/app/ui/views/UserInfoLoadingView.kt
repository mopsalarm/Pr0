package com.pr0gramm.app.ui.views

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.pr0gramm.app.R
import com.pr0gramm.app.UserClassesService
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.find

class UserInfoLoadingView(context: Context) : FrameLayout(context) {
    private val userClassesService = context.injector.instance<UserClassesService>()
    private val usernameView: UsernameView
    private val usermarkView: TextView

    init {
        View.inflate(context, R.layout.user_info_loading, this)
        usernameView = find(R.id.username)
        usermarkView = find(R.id.user_type_name)

        find<View>(R.id.kpi_benis).isVisible = false
        find<View>(R.id.busy_indicator).isVisible = true
    }

    fun update(name: String, mark: Int) {
        usernameView.setUsername(name, mark)

        val userClass = userClassesService.get(mark)
        usermarkView.text = userClass.name
        usermarkView.setTextColor(userClass.color)
    }
}