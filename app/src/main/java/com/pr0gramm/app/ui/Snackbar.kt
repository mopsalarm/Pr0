package com.pr0gramm.app.ui

import android.view.ViewGroup
import androidx.core.view.ViewCompat
import com.google.android.material.snackbar.Snackbar
import com.pr0gramm.app.R
import com.pr0gramm.app.util.dip2px

fun Snackbar.configureNewStyle(): Snackbar {
    val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return this

    val margin = context.dip2px(12f).toInt()
    params.setMargins(margin, margin, margin, margin)
    this.view.layoutParams = params

    this.view.setBackgroundResource(R.drawable.snackbar)

    ViewCompat.setElevation(this.view, context.dip2px(6f))

    return this
}