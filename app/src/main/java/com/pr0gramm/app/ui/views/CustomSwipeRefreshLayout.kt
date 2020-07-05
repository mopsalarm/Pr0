package com.pr0gramm.app.ui.views

import android.content.Context
import android.util.AttributeSet
import com.pr0gramm.app.util.AndroidUtility

/**
 */
class CustomSwipeRefreshLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : androidx.swiperefreshlayout.widget.SwipeRefreshLayout(context, attrs) {
    private var canScrollUpTest: (() -> Boolean)? = null

    fun setCanChildScrollUpTest(test: () -> Boolean) {
        canScrollUpTest = test
    }

    override fun canChildScrollUp(): Boolean {
        return (canScrollUpTest?.invoke() ?: false) || super.canChildScrollUp()
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
