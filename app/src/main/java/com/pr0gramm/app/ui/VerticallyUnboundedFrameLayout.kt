package com.pr0gramm.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 */
class VerticallyUnboundedFrameLayout @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    override fun measureChildWithMargins(child: View,
                                         parentWidthMeasureSpec: Int, widthUsed: Int,
                                         parentHeightMeasureSpec: Int, heightUsed: Int) {

        super.measureChildWithMargins(child,
                parentWidthMeasureSpec, widthUsed,
                View.MeasureSpec.UNSPECIFIED, heightUsed)
    }
}
