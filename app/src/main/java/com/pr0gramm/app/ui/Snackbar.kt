package com.pr0gramm.app.ui

import android.app.Activity
import android.view.ViewGroup
import com.google.android.material.snackbar.Snackbar
import com.pr0gramm.app.R
import com.pr0gramm.app.util.dip2px

fun Snackbar.configureNewStyle(parent: Activity? = null): Snackbar {
    val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return this

    val margin = context.dip2px(12f).toInt()
    params.setMargins(margin, margin, margin, margin)

    if (parent is ScrollHideToolbarListener.ToolbarActivity) {
        parent.rxWindowInsets.take(1).subscribe { insets ->
            params.bottomMargin += insets.bottom
        }
    }

    this.view.layoutParams = params

    this.view.setBackgroundResource(R.drawable.snackbar)

    this.view.elevation = context.dip2px(6f)

    return this
}