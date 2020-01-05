package com.pr0gramm.app.ui

import android.graphics.Color
import android.view.ViewGroup
import androidx.core.view.isEmpty
import com.google.android.material.snackbar.Snackbar
import com.pr0gramm.app.R
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.dp

fun Snackbar.configureNewStyle(): Snackbar {

    // this.view is the layout holding the actual snackbar content.
    // so we get that one and convert it to a ViewGroup
    val snackbarLayout = this.view as? ViewGroup ?: return this
    if (snackbarLayout.isEmpty()) {
        return this
    }

    // now we can get the actual snackbar content.
    val snackbarView = snackbarLayout.getChildAt(0)

    val params = snackbarView.layoutParams as? ViewGroup.MarginLayoutParams ?: return this
    params.bottomMargin += context.dp(12)

    val activity = AndroidUtility.activityFromContext(context)
    if (activity is ScrollHideToolbarListener.ToolbarActivity) {
        activity.rxWindowInsets.take(1).subscribe { insets ->
            params.bottomMargin += insets.bottom
        }
    }

    snackbarView.layoutParams = params

    snackbarView.setBackgroundResource(R.drawable.snackbar)

    snackbarView.elevation = context.dp(6f)

    // make layout holding the actual snackbar transparent.
    snackbarLayout.setBackgroundColor(Color.TRANSPARENT)


    return this
}