package com.pr0gramm.app.ui.views

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.pr0gramm.app.R
import com.pr0gramm.app.util.observeChange
import com.pr0gramm.app.util.use
import kotlin.math.abs

/**
 * A [FrameLayout] that keeps a aspect ratio and calculates it height
 * from the width.
 */
open class AspectLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {
    var aspect: Float by observeChange(-1f) { requestLayout() }

    init {
        aspect = context.theme.obtainStyledAttributes(attrs, R.styleable.AspectView, 0, 0).use {
            it.getFloat(R.styleable.AspectView_aspect, 1f)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (aspect < 0) {
            // Aspect ratio not set.
            return
        }

        val width = measuredWidth
        var height = measuredHeight
        val viewAspectRatio = width.toFloat() / height
        val aspectDeformation = aspect / viewAspectRatio - 1
        if (abs(aspectDeformation) <= MAX_ASPECT_RATIO_DEFORMATION_FRACTION) {
            // We're within the allowed tolerance.
            return
        }

        // always "fix" height, never change width.
        height = (width / aspect).toInt()

        super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

    companion object {
        private const val MAX_ASPECT_RATIO_DEFORMATION_FRACTION = 0.01f
    }
}