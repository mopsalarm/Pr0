package com.pr0gramm.app.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.pr0gramm.app.R
import com.pr0gramm.app.util.observeChange
import com.pr0gramm.app.util.use

/**
 */
class MaxWidthFrameLayout @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var maxWidth: Int by observeChange(0) { requestLayout() }

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.MaxWidthFrameLayout, 0, 0).use {
            maxWidth = it.getDimensionPixelSize(
                    R.styleable.MaxWidthFrameLayout_mwfl_maxWidth, Integer.MAX_VALUE)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)

        var widthSpec = widthMeasureSpec
        if (width > maxWidth) {
            if (mode == MeasureSpec.UNSPECIFIED || mode == MeasureSpec.AT_MOST) {
                widthSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST)
            }
        }

        super.onMeasure(widthSpec, heightMeasureSpec)
    }
}
