package com.pr0gramm.app.ui

import android.view.ViewGroup
import com.google.android.material.snackbar.Snackbar
import com.pr0gramm.app.R
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.dip2px

fun Snackbar.configureNewStyle(): Snackbar {
    val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return this

    val margin = context.dip2px(12f).toInt()
    params.setMargins(margin, margin, margin, margin)

    val activity = AndroidUtility.activityFromContext(context)
    if (activity is ScrollHideToolbarListener.ToolbarActivity) {
        activity.rxWindowInsets.take(1).subscribe { insets ->
            params.bottomMargin += insets.bottom
        }
    }

    this.view.layoutParams = params

    this.view.setBackgroundResource(R.drawable.snackbar)

    this.view.elevation = context.dip2px(6f)

    return this
}