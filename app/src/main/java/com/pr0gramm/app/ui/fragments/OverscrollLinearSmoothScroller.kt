package com.pr0gramm.app.ui.fragments

import android.content.Context
import android.support.v7.widget.LinearSmoothScroller
import android.view.View
import com.pr0gramm.app.util.AndroidUtility

class OverscrollLinearSmoothScroller(private val context: Context) : LinearSmoothScroller(context) {
    val offset = AndroidUtility.getActionBarContentOffset(context)

    override fun calculateDyToMakeVisible(view: View?, snapPreference: Int): Int {
        val dy = super.calculateDyToMakeVisible(view, snapPreference)
        return dy + offset + AndroidUtility.dp(context, 32)
    }

    override fun getVerticalSnapPreference(): Int = SNAP_TO_START
}
