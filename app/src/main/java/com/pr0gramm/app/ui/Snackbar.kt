package com.pr0gramm.app.ui

import android.view.ViewGroup
import com.google.android.material.snackbar.Snackbar
import com.pr0gramm.app.R
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.dp

fun Snackbar.configureNewStyle(): Snackbar {
    val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return this

    val margin = context.dp(12)
    params.setMargins(margin, margin, margin, margin)

    val activity = AndroidUtility.activityFromContext(context)
    if (activity is ScrollHideToolbarListener.ToolbarActivity) {
        activity.rxWindowInsets.take(1).subscribe { insets ->
            val offset = if (insets.bottom == 0) context.dp(64) else insets.bottom
            params.bottomMargin += offset
        }
    }

    this.view.layoutParams = params

    this.view.setBackgroundResource(R.drawable.snackbar)

    this.view.elevation = context.dp(6f)

    return this
}