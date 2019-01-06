package com.pr0gramm.app.ui

import android.view.View
import android.view.ViewPropertyAnimator
import com.pr0gramm.app.util.visible

/**
 */
class ScrollHideToolbarListener(private val toolbar: View) {
    private var toolbarMarginOffset: Int = 0
    private var animation: ViewPropertyAnimator? = null
    private var hidden: Boolean = false

    init {

        toolbar.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val newHeight = bottom - top
            val oldHeight = oldBottom - oldTop

            if (oldHeight > 0 && newHeight != oldHeight && toolbarMarginOffset == oldHeight) {
                toolbarMarginOffset = newHeight
                applyToolbarPosition(false)
            }
        }
    }

    private fun applyToolbarPosition(animated: Boolean) {
        // do not do anything if hidden
        if (hidden) return

        // stop any previous animation
        animation?.cancel()
        animation = null

        val y = -toolbarMarginOffset
        val targetVisible = toolbar.height > toolbarMarginOffset
        if (animated) {
            if (targetVisible) {
                toolbar.visible = true
            }

            animation = toolbar.animate()
                    .translationY(y.toFloat())
                    .setDuration(250)
                    .withEndAction {
                        if (!targetVisible) {
                            toolbar.visible = false
                        }
                    }
                    .apply { start() }
        } else {
            toolbar.translationY = y.toFloat()
            toolbar.visible = targetVisible
        }
    }

    fun onScrolled(dy: Int) {
        // do not do anything if hidden
        if (hidden) return

        val abHeight = toolbar.height
        if (abHeight == 0)
            return

        toolbarMarginOffset += dy
        if (toolbarMarginOffset >= abHeight) {
            toolbarMarginOffset = abHeight
        }

        if (toolbarMarginOffset < 0)
            toolbarMarginOffset = 0

        applyToolbarPosition(false)
    }

    fun onScrollFinished(y: Int) {
        // do not do anything if hidden
        if (hidden) return

        val abHeight = toolbar.height
        if (abHeight == 0)
            return

        if (y < abHeight) {
            reset()
        } else {
            toolbarMarginOffset = if (toolbarMarginOffset > abHeight / 2) abHeight else 0
            applyToolbarPosition(true)
        }
    }

    fun reset() {
        hidden = false

        if (toolbarMarginOffset != 0) {
            toolbarMarginOffset = 0
            applyToolbarPosition(true)
        }
    }

    val toolbarHeight: Int
        get() = toolbar.height

    val visibleHeight: Int
        get() = (toolbar.height + toolbar.translationY).toInt()

    fun hide() {
        if (toolbarMarginOffset != toolbar.height) {
            toolbarMarginOffset = toolbar.height
            applyToolbarPosition(true)

            hidden = true
        }
    }

    interface ToolbarActivity {
        val scrollHideToolbarListener: ScrollHideToolbarListener
    }

    companion object {

        /**
         * This method estimates scrolling based on y value of the first element
         * in this recycler view. If scrolling could not be estimated, an empty optional
         * will be returned.

         * @param recyclerView The recycler view to estimate scrolling of
         */

        fun estimateRecyclerViewScrollY(recyclerView: androidx.recyclerview.widget.RecyclerView): Int? {
            var scrollY: Int? = null
            val view = recyclerView.layoutManager?.findViewByPosition(0)
            if (view != null) {
                scrollY = -view.y.toInt()
            }

            return scrollY
        }
    }
}
