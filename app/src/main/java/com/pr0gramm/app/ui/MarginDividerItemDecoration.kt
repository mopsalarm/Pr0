package com.pr0gramm.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.withSave
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.util.dip2px
import com.pr0gramm.app.util.use

class MarginDividerItemDecoration(context: Context, marginLeftDp: Int = 0, marginRightDp: Int = 0) : RecyclerView.ItemDecoration() {
    private val bounds = Rect()

    val marginLeft: Int = context.dip2px(marginLeftDp)
    val marginRight: Int = context.dip2px(marginRightDp)

    private val divider: Drawable = run {
        context.obtainStyledAttributes(intArrayOf(android.R.attr.listDivider)).use { a ->
            a.getDrawable(0) ?: throw IllegalArgumentException("Use a theme with listDivider")
        }
    }


    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.set(0, 0, 0, divider.intrinsicHeight)
    }


    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (parent.layoutManager == null) {
            return
        }

        drawVertical(c, parent)
    }

    private fun drawVertical(canvas: Canvas, parent: RecyclerView) = canvas.withSave {
        val left: Int
        val right: Int

        if (parent.clipToPadding) {
            left = parent.paddingLeft + marginLeft
            right = parent.width - parent.paddingRight + marginRight

            val bottom = parent.height - parent.paddingBottom
            canvas.clipRect(left, parent.paddingTop, right, bottom)
        } else {
            left = marginLeft
            right = parent.width + marginRight
        }

        for (i in 0 until parent.childCount - 1) {
            val child = parent.getChildAt(i)
            parent.getDecoratedBoundsWithMargins(child, bounds)

            val bottom = bounds.bottom + Math.round(child.translationY)
            val top = bottom - divider.intrinsicHeight
            divider.setBounds(left, top, right, bottom)
            divider.draw(canvas)
        }
    }
}