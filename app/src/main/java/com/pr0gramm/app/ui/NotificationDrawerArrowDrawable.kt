package com.pr0gramm.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import com.pr0gramm.app.R
import com.pr0gramm.app.util.getStyledColor
import com.pr0gramm.app.util.observeChange

class NotificationDrawerArrowDrawable(private val context: Context) : DrawerArrowDrawable(context) {
    var hasNotification: Boolean by observeChange(false) { invalidateSelf() }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        if (hasNotification) {
            drawNotificationDot(canvas)
        }
    }

    private fun drawNotificationDot(canvas: Canvas) {
        val bounds = bounds

        val dp = context.resources.displayMetrics.density
        val centerX = bounds.exactCenterX() + 8 * dp
        val centerY = bounds.exactCenterY() - 8 * dp

        val p = Paint()
        p.isAntiAlias = true
        p.color = context.getStyledColor(R.attr.badgeColor)
        canvas.drawCircle(centerX, centerY, 4 * dp, p)
    }
}
