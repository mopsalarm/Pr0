package com.pr0gramm.app.ui.views.viewer

import android.annotation.SuppressLint
import android.view.MotionEvent
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.base.whileIsAttachedScope
import kotlinx.coroutines.launch

/**
 */
@SuppressLint("ViewConstructor")
abstract class ProxyMediaView internal constructor(config: Config) : MediaView(config, R.layout.player_proxy) {
    private var delegate: MediaView? = null

    init {
        showBusyIndicator()
    }

    internal fun setChild(child: MediaView) {
        hideBusyIndicator()

        var idx = childCount

        val previewView = previewView
        if (previewView?.parent === this) {
            idx = indexOfChild(previewView) + 1
        }

        child.layoutParams = layoutParams
        child.viewAspect = viewAspect

        addView(child, idx)

        delegate = child

        if (isPlaying) {
            child.playMedia()
        }

        // forward double clicks
        child.tapListener = ForwardingTapListener()

        child.wasViewed = { onMediaShown() }

        whileIsAttachedScope {
            launch {
                child.controllerViews().collect { view -> publishControllerView(view) }
            }
        }
    }

    override fun playMedia() {
        super.playMedia()
        delegate?.playMedia()
    }

    override fun stopMedia() {
        super.stopMedia()
        delegate?.stopMedia()
    }

    override fun rewind() {
        delegate?.rewind()
    }

    override val actualMediaView: MediaView
        get() = delegate ?: this

    override fun onMediaShown() {
        viewAspect = -1f
        removePreviewImage()
        super.onMediaShown()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        delegate?.let { child ->
            event.offsetLocation(
                    (child.paddingLeft - paddingLeft).toFloat(),
                    (child.paddingTop - paddingTop).toFloat())

            return child.onTouchEvent(event)
        }

        return super.onTouchEvent(event)
    }

    private inner class ForwardingTapListener : TapListener {
        override fun onSingleTap(event: MotionEvent): Boolean {
            return tapListener?.onSingleTap(event) ?: false
        }

        override fun onDoubleTap(event: MotionEvent): Boolean {
            return tapListener?.onDoubleTap(event) ?: false
        }
    }
}
