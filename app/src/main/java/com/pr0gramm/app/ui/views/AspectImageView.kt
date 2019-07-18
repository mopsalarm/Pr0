package com.pr0gramm.app.ui.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.pr0gramm.app.R
import com.pr0gramm.app.util.use

/**
 * A image that measures it height to the same value
 * as its width.
 */
class AspectImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        AppCompatImageView(context, attrs, defStyleAttr) {

    /**
     * Aspect ratio of the view. The view will always fill the maximum width but will
     * set its height using the given aspect ratio. If the aspect ratio is negative,
     * this view behaves like a normal image view.
     */
    var aspect: Float = 0.toFloat()
        get() {
            return if (field > 0) {
                field
            } else {
                drawable?.run { intrinsicWidth.toFloat() / intrinsicHeight }?.takeIf { it > 0 }
                        ?: 1.75f
            }
        }
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    init {
        // apply attributes
        aspect = context.theme.obtainStyledAttributes(attrs, R.styleable.AspectView, 0, 0).use {
            it.getFloat(R.styleable.AspectView_aspect, 1f)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val width = measuredWidth
        setMeasuredDimension(width, (width / aspect).toInt())
    }
}
