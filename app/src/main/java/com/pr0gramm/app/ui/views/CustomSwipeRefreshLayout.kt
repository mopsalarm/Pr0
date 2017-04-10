package com.pr0gramm.app.ui.views

import android.content.Context
import android.support.v4.widget.SwipeRefreshLayout
import android.util.AttributeSet

import com.pr0gramm.app.util.AndroidUtility

/**
 */
class CustomSwipeRefreshLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : SwipeRefreshLayout(context, attrs) {
    var canSwipeUpPredicate: () -> Boolean = { false }

    override fun canChildScrollUp(): Boolean {
        return canSwipeUpPredicate() || super.canChildScrollUp()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        try {
            super.onLayout(changed, left, top, right, bottom)
        } catch (err: Exception) {
            // I found crashlytics reports during layout,
            // lets just catch everything inside this layout.
            AndroidUtility.logToCrashlytics(err)
        }
    }
}
