package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.support.v4.content.ContextCompat
import android.support.v7.graphics.drawable.DrawableWrapper
import android.support.v7.widget.AppCompatTextView
import android.util.AttributeSet
import com.pr0gramm.app.UserClasses
import com.pr0gramm.app.util.AndroidUtility.dp


/**
 */
class UsernameView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        compoundDrawablePadding = dp(context, 2)

        if (isInEditMode) {
            setUsername("Mopsalarm", 2)
        }
    }

    var mark: Int = 4
        set(v) {
            val mark = v.takeUnless { it < 0 || it >= UserClasses.MarkDrawables.size } ?: 4
            field = mark

            val circle = ContextCompat.getDrawable(context, UserClasses.MarkDrawables[mark])

            val wrapped = BaselineCompoundDrawable(circle)
            wrapped.setBounds(0, 0, circle.intrinsicWidth, height)
            setCompoundDrawables(null, null, wrapped, null)
        }

    @SuppressLint("SetTextI18n")
    fun setUsername(name: String, mark: Int) {
        this.text = name
        this.mark = mark
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // resize the mark-drawable to the height of the image.
        compoundDrawables[2]?.let { d ->
            d.setBounds(0, 0, d.intrinsicWidth, h)
            setCompoundDrawables(null, null, d, null)
        }
    }

    private inner class BaselineCompoundDrawable(mDrawable: Drawable) : DrawableWrapper(mDrawable) {
        override fun draw(canvas: Canvas) {
            wrappedDrawable.setBounds(0, baseline - intrinsicHeight, intrinsicWidth, baseline)
            wrappedDrawable.draw(canvas)
        }
    }
}
