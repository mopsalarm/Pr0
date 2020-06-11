package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.text.PrecomputedText
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.PrecomputedTextCompat
import com.pr0gramm.app.Logger
import com.pr0gramm.app.time
import com.pr0gramm.app.util.NonCrashingLinkMovementMethod


class CompatibleTextView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val logger = Logger("CompatibleTextView")

    init {
        movementMethod = NonCrashingLinkMovementMethod
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val currentText = text

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && currentText is PrecomputedText) {
            logger.debug { "Replace PrecomputedText with actual text on touch" }

            normalizeText()
            dispatchMotionEventAgain(event)

            return true
        }

        if (currentText is PrecomputedTextCompat) {
            logger.debug { "Replace PrecomputedTextCompat with actual text on touch" }

            normalizeText()
            dispatchMotionEventAgain(event)

            return true
        }

        return super.dispatchTouchEvent(event)
    }

    private fun normalizeText() {
        logger.time("normalizeText") {
            val inputText = text
            val normalText = PrecomputedTextAccessors.textOf(inputText) ?: inputText.toString()
            setText(normalText, BufferType.SPANNABLE)
        }
    }

    private fun dispatchMotionEventAgain(event: MotionEvent) {
        val eventCopy = MotionEvent.obtain(event)

        post {
            dispatchTouchEvent(eventCopy)
            eventCopy.recycle()
        }
    }
}

@SuppressLint("NewApi")
private object PrecomputedTextAccessors {
    private val logger = Logger("PrecomputedTextAccessors")

    fun textOf(inputText: CharSequence): CharSequence? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (inputText is PrecomputedText) {
                return getTextOf(inputText)
            }
        }

        if (inputText is PrecomputedTextCompat) {
            return getTextOf(inputText)
        }

        return inputText.toString()
    }

    private fun getTextOf(obj: PrecomputedText): CharSequence? {
        try {
            val fields = PrecomputedText::class.java.declaredFields
            for (field in fields) {
                if (!CharSequence::class.java.isAssignableFrom(field.type)) {
                    continue
                }

                field.isAccessible = true

                val fieldValue = field.get(obj) as CharSequence?
                if (fieldValue !is PrecomputedText && fieldValue !is PrecomputedTextCompat) {
                    return fieldValue
                }
            }

        } catch (err: Exception) {
            logger.warn(err) { "Could not get text from ${obj.javaClass}" }
        }

        // did not find anything
        return null
    }


    private fun getTextOf(obj: PrecomputedTextCompat): CharSequence? {
        try {
            val fields = PrecomputedTextCompat::class.java.declaredFields
            for (field in fields) {
                if (!CharSequence::class.java.isAssignableFrom(field.type)) {
                    continue
                }

                field.isAccessible = true

                val fieldValue = field.get(obj) as CharSequence?
                if (fieldValue != null) {
                    return textOf(fieldValue)
                }
            }

        } catch (err: Exception) {
            logger.warn(err) { "Could not get text from ${obj.javaClass}" }
        }

        // did not find anything
        return null
    }
}

