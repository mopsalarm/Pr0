package com.pr0gramm.app.util

import android.text.Selection
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.method.Touch
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.View
import android.widget.TextView

/**
 * Found it on stack overflow. Translated to kotlin.
 */
object NonCrashingLinkMovementMethod : LinkMovementMethod() {
    override fun initialize(widget: TextView, text: Spannable) {
        setSelection(text)
    }

    override fun onTakeFocus(view: TextView, text: Spannable, dir: Int) {
        if (dir and (View.FOCUS_FORWARD or View.FOCUS_DOWN) != 0) {
            if (view.layout == null) {
                // This shouldn't be null, but do something sensible if it is.
                setSelection(text)
            }
        } else {
            setSelection(text)
        }
    }

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {

        val action = event.action

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            val x = event.x.toInt() - widget.totalPaddingLeft + widget.scaleX
            val y = event.y.toInt() - widget.totalPaddingTop + widget.scaleY

            val layout = widget.layout
            val line = layout.getLineForVertical(y.toInt())
            val off = layout.getOffsetForHorizontal(line, x)

            val link = buffer.getSpans(off, off, ClickableSpan::class.java)

            if (link.isNotEmpty()) {
                if (action == MotionEvent.ACTION_UP) {
                    link[0].onClick(widget)
                } else {
                    Selection.setSelection(buffer,
                            buffer.getSpanStart(link[0]),
                            buffer.getSpanEnd(link[0]))
                }
                return true
            }
        }

        return Touch.onTouchEvent(widget, buffer, event)
    }

    private fun setSelection(text: Spannable) {
        try {
            Selection.setSelection(text, text.length)
        } catch (ignored: Exception) {
        }
    }
}
