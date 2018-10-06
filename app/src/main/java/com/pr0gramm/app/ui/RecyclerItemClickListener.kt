package com.pr0gramm.app.ui


import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import rx.Observable
import rx.subjects.PublishSubject

class RecyclerItemClickListener(private val recyclerView: androidx.recyclerview.widget.RecyclerView) {
    private val mGestureDetector: GestureDetector

    private val touchListener = Listener()
    private val scrollListener = ScrollListener()

    private val itemClicked = PublishSubject.create<View>()
    private val itemLongClicked = PublishSubject.create<View>()
    private val itemLongClickedEnded = PublishSubject.create<Void>()
    private var longClickEnabled: Boolean = false

    private var longPressTriggered: Boolean = false

    init {
        mGestureDetector = GestureDetector(recyclerView.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (longClickEnabled) {
                    val childView = recyclerView.findChildViewUnder(e.x, e.y)
                    if (childView != null) {
                        longPressTriggered = true
                        itemLongClicked.onNext(childView)
                    }
                }
            }
        })

        recyclerView.addOnItemTouchListener(touchListener)
        recyclerView.addOnScrollListener(scrollListener)
    }

    fun itemClicked(): Observable<View> {
        return itemClicked.asObservable()
    }

    fun itemLongClicked(): Observable<View> {
        return itemLongClicked.asObservable()
    }

    fun itemLongClickEnded(): Observable<Void> {
        return itemLongClickedEnded.asObservable()
    }

    fun enableLongClick(enabled: Boolean) {
        longClickEnabled = enabled
        longPressTriggered = longPressTriggered and enabled
    }

    private inner class Listener internal constructor() : androidx.recyclerview.widget.RecyclerView.SimpleOnItemTouchListener() {

        override fun onInterceptTouchEvent(view: androidx.recyclerview.widget.RecyclerView, e: MotionEvent): Boolean {
            val childView = view.findChildViewUnder(e.x, e.y)

            if (childView != null && mGestureDetector.onTouchEvent(e)) {
                itemClicked.onNext(childView)
            }

            // a long press might have been triggered between the last touch event
            // and the current. we use this info to start tracking the current touch
            // if that happened.
            val intercept = longClickEnabled && longPressTriggered
                    && e.action != MotionEvent.ACTION_DOWN

            longPressTriggered = false

            if (intercept) {
                // actually this click right now might have triggered the long press
                onTouchEvent(view, e)
            }

            return intercept
        }

        override fun onTouchEvent(view: androidx.recyclerview.widget.RecyclerView, motionEvent: MotionEvent) {
            when (motionEvent.action) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_HOVER_EXIT, MotionEvent.ACTION_POINTER_UP ->
                    itemLongClickedEnded.onNext(null)
            }
        }
    }

    private inner class ScrollListener internal constructor() : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
            when (newState) {
                androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING, androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_SETTLING ->
                    recyclerView.removeOnItemTouchListener(touchListener)

                androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE -> {
                    // ensure that the listener is only added once
                    recyclerView.removeOnItemTouchListener(touchListener)
                    recyclerView.addOnItemTouchListener(touchListener)
                }
            }
        }
    }
}
