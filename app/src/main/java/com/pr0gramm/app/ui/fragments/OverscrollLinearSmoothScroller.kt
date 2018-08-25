package com.pr0gramm.app.ui.fragments

import android.content.Context
import android.support.v7.widget.LinearSmoothScroller
import android.view.View
import com.pr0gramm.app.util.AndroidUtility

class OverscrollLinearSmoothScroller(
        context: Context,
        private val baseOffset: Int = AndroidUtility.dp(context, 32),
        private val verticalSnapPreference: Int? = null) : LinearSmoothScroller(context) {

    private val offset = AndroidUtility.getActionBarContentOffset(context)

    override fun calculateDyToMakeVisible(view: View?, snapPreference: Int): Int {
        val dy = super.calculateDyToMakeVisible(view, snapPreference)

        val offset = if (snapPreference == SNAP_TO_START) baseOffset + offset else -baseOffset
        return dy + offset
    }

    override fun getVerticalSnapPreference(): Int {
        return verticalSnapPreference ?: super.getVerticalSnapPreference()
    }
}
