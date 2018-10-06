package com.pr0gramm.app.ui.fragments

import android.content.Context
import kotlin.math.absoluteValue

class OverscrollLinearSmoothScroller(
        context: Context, idx: Int,
        private val dontScrollIfVisible: Boolean,
        private val offsetTop: Int,
        private val offsetBottom: Int) : androidx.recyclerview.widget.LinearSmoothScroller(context) {

    init {
        targetPosition = idx
    }

    override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int {
        val dTop = boxStart - (viewStart - offsetTop)
        val dBot = boxEnd - (viewEnd + offsetBottom)
        return if (dTop.absoluteValue < dBot.absoluteValue) {
            if (dontScrollIfVisible && dTop < 0) 0 else dTop
        } else {
            if (dontScrollIfVisible && dBot > 0) 0 else dBot
        }
    }
}

class CenterLinearSmoothScroller(ctx: Context, idx: Int) : androidx.recyclerview.widget.LinearSmoothScroller(ctx) {
    init {
        targetPosition = idx
    }

    override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int {
        return boxStart + (boxEnd - boxStart) / 2 - (viewStart + (viewEnd - viewStart) / 2)
    }
}
