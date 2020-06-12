package com.pr0gramm.app.ui.views

import android.content.Context
import android.os.Build
import android.text.PrecomputedText
import android.text.SpannedString
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.PrecomputedTextCompat
import com.pr0gramm.app.Logger
import com.pr0gramm.app.util.NonCrashingLinkMovementMethod


class CompatibleTextView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val logger = Logger("CompatibleTextView")

    init {
        movementMethod = NonCrashingLinkMovementMethod
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val currentText = text

        val isPrecomputedText = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && currentText is PrecomputedText
        val isPrecomputedTextCompat = currentText is PrecomputedTextCompat

        if (isPrecomputedText || isPrecomputedTextCompat) {
            logger.debug { "Replace PrecomputedText with actual text on touch" }

            // do a non precomputed copy of the string.
            text = SpannedString(currentText)

            // and dispatch the event again in the next frame.
            dispatchMotionEventAgain(event)

            return true
        }

        return super.dispatchTouchEvent(event)
    }

    private fun dispatchMotionEventAgain(event: MotionEvent) {
        val eventCopy = MotionEvent.obtain(event)

        post {
            dispatchTouchEvent(eventCopy)
            eventCopy.recycle()
        }
    }
}
