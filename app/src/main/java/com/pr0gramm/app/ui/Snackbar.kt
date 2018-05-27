package com.pr0gramm.app.ui

import android.support.design.widget.Snackbar
import android.support.v4.view.ViewCompat
import android.view.ViewGroup
import com.pr0gramm.app.R
import com.pr0gramm.app.util.dp2px

fun Snackbar.configureNewStyle(): Snackbar {
    val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return this

    val margin = context.dp2px(12f).toInt()
    params.setMargins(margin, margin, margin, margin)
    this.view.layoutParams = params

    this.view.setBackgroundResource(R.drawable.snackbar)

    ViewCompat.setElevation(this.view, context.dp2px(6f))

    return this
}