package com.pr0gramm.app.ui.views.viewer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import com.pr0gramm.app.R
import com.pr0gramm.app.util.removeFromParent
import java.util.concurrent.atomic.AtomicBoolean

/**
 */
@SuppressLint("ViewConstructor")
class DelayedMediaView(config: MediaView.Config) : ProxyMediaView(config) {
    private val overlay: View
    private val childCreated = AtomicBoolean()

    init {
        hideBusyIndicator()

        overlay = LayoutInflater.from(context).inflate(R.layout.player_delayed_overlay, this, false)

        // Display the overlay in a smooth animation
        overlay.alpha = 0f
        overlay.scaleX = 0.8f
        overlay.scaleY = 0.8f
        overlay.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(300).start()

        addView(overlay)
    }

    override fun onSingleTap(event: MotionEvent): Boolean {
        // call this function only exactly once!
        if (!childCreated.compareAndSet(false, true))
            return false

        // create the real view as a child.
        val mediaView = MediaViews.newInstance(config)

        mediaView.removePreviewImage()
        setChild(mediaView)

        overlay.animate()
                .alpha(0f).scaleX(0.8f).scaleY(0.8f)
                .withEndAction { overlay.removeFromParent() }
                .start()

        return true
    }
}
