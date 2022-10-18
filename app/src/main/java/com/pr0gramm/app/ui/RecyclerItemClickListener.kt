package com.pr0gramm.app.ui


import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.Logger
import com.pr0gramm.app.util.OnClickListener
import com.pr0gramm.app.util.OnViewClickListener
import com.pr0gramm.app.util.invoke

class RecyclerItemClickListener(private val recyclerView: RecyclerView) {
    private val logger = Logger("RecyclerItemClickListener")

    private var touchListener: Listener? = null
    private val scrollListener = ScrollListener()

    private var longClickEnabled: Boolean = false
    private var longPressJustTriggered: Boolean = false

    private var ignoreLongTap: Boolean = false

    var itemClicked: OnViewClickListener? = null
    var itemLongClicked: OnViewClickListener? = null
    var itemLongClickEnded: OnClickListener? = null

    init {
        recyclerView.addOnScrollListener(scrollListener)

        touchListener = Listener().also { listener ->
            recyclerView.addOnItemTouchListener(listener)
        }
    }

    fun enableLongClick(enabled: Boolean) {
        longClickEnabled = enabled
        longPressJustTriggered = longPressJustTriggered and enabled
    }

    private var lastChildView: View? = null

    private inner class Listener : RecyclerView.SimpleOnItemTouchListener() {
        private val mGestureDetector =
            GestureDetector(recyclerView.context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    lastChildView?.let { childView ->
                        itemClicked?.invoke(childView)
                    }

                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    if (touchListener !== this@Listener) {
                        return
                    }

                    logger.debug { "LongPress event detected: $e" }
                    if (longClickEnabled) {
                        val childView = recyclerView.findChildViewUnder(e.x, e.y)
                        if (childView != null) {
                            longPressJustTriggered = true
                            itemLongClicked(childView)
                        }
                    }
                }
            })

        override fun onInterceptTouchEvent(view: RecyclerView, e: MotionEvent): Boolean {
            if (touchListener !== this) {
                return false
            }

            lastChildView = view.findChildViewUnder(e.x, e.y)
            if (mGestureDetector.onTouchEvent(e)) {
                logger.debug { "TouchEvent intercepted: $e" }
                return true
            }

            logger.debug { "Touch event was not intercepted: $e" }

            // a long press might have been triggered between the last touch event
            // and the current. we use this info to start tracking the current long touch
            // if that happened.
            val intercept = longClickEnabled && longPressJustTriggered && e.action != MotionEvent.ACTION_DOWN

            longPressJustTriggered = false

            if (intercept) {
                // actually this click right now might be the end of the long press, so push it to onTouchEvent too
                onTouchEvent(view, e)
            }

            return intercept
        }

        override fun onTouchEvent(view: RecyclerView, motionEvent: MotionEvent) {
            logger.debug { "onTouchEvent($motionEvent)" }

            when (motionEvent.action) {
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_HOVER_EXIT,
                MotionEvent.ACTION_POINTER_UP ->
                    itemLongClickEnded()
            }
        }
    }

    private inner class ScrollListener : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            when (newState) {
                RecyclerView.SCROLL_STATE_DRAGGING, RecyclerView.SCROLL_STATE_SETTLING -> {
                    touchListener?.let { listener ->
                        recyclerView.removeOnItemTouchListener(listener)
                    }

                    touchListener = null

                    ignoreLongTap = true
                }

                RecyclerView.SCROLL_STATE_IDLE -> {
                    touchListener?.let { listener ->
                        recyclerView.removeOnItemTouchListener(listener)
                    }

                    touchListener = Listener().also { listener ->
                        recyclerView.addOnItemTouchListener(listener)
                    }
                }
            }
        }
    }
}
