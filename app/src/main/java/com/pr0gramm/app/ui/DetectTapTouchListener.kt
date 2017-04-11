package com.pr0gramm.app.ui

import android.view.MotionEvent
import android.view.View
import rx.functions.Action0

/**
 * Detects single taps.
 */
class DetectTapTouchListener private constructor(private val consumer: Action0) : View.OnTouchListener {
    private var moveOccurred: Boolean = false
    private var firstX: Float = 0.toFloat()
    private var firstY: Float = 0.toFloat()

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (event.actionIndex == 0) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                moveOccurred = false
                firstX = event.x
                firstY = event.y
            }

            if (event.action == MotionEvent.ACTION_MOVE) {
                moveOccurred = moveOccurred
                        || Math.abs(event.x - firstX) > 32
                        || Math.abs(event.y - firstY) > 32
            }

            if (event.action == MotionEvent.ACTION_UP) {
                if (!moveOccurred) {
                    consumer.call()
                    return true
                }
            }
        }

        return false
    }

    companion object {
        @JvmStatic
        fun withConsumer(consumer: Action0): DetectTapTouchListener {
            return DetectTapTouchListener(consumer)
        }
    }
}
