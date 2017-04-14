package com.pr0gramm.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.support.v7.widget.AppCompatTextView
import android.util.AttributeSet
import android.view.View

import com.pr0gramm.app.util.AndroidUtility

/**
 */
class ExceptionCatchingTextView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    @SuppressLint("DrawAllocation")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        try {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        } catch (error: Exception) {
            AndroidUtility.logToCrashlytics(TextRenderException(error))

            setMeasuredDimension(
                    View.getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
                    View.getDefaultSize(suggestedMinimumHeight, heightMeasureSpec))
        }

    }

    override fun draw(canvas: Canvas) {
        try {
            super.draw(canvas)
        } catch (error: Exception) {
            AndroidUtility.logToCrashlytics(TextRenderException(error))
        }

    }

    override fun onPreDraw(): Boolean {
        try {
            return super.onPreDraw()
        } catch (error: Exception) {
            AndroidUtility.logToCrashlytics(TextRenderException(error))
            return true
        }

    }

    override fun getBaseline(): Int {
        try {
            return super.getBaseline()
        } catch (error: Exception) {
            AndroidUtility.logToCrashlytics(TextRenderException(error))
            return -1
        }

    }

    private class TextRenderException internal constructor(cause: Throwable) : RuntimeException(cause)
}
