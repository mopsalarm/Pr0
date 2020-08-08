package com.pr0gramm.app.ui.fragments.post

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.leinardi.android.speeddial.SpeedDialView

class ScrollingViewSnackbarBehavior : SpeedDialView.ScrollingViewSnackbarBehavior {
    constructor() : super()
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun onStartNestedScroll(
            coordinatorLayout: CoordinatorLayout,
            child: View, directTargetChild: View, target: View, nestedScrollAxes: Int, type: Int,
    ): Boolean {
        return nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL
    }

    override fun onNestedScroll(coordinatorLayout: CoordinatorLayout, child: View, target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, type: Int, consumed: IntArray) {
        onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type)
    }
}