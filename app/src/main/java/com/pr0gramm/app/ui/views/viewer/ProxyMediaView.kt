package com.pr0gramm.app.ui.views.viewer

import android.annotation.SuppressLint
import android.view.MotionEvent
import com.pr0gramm.app.R
import rx.subscriptions.CompositeSubscription

/**
 */
@SuppressLint("ViewConstructor")
abstract class ProxyMediaView internal constructor(config: MediaView.Config) : MediaView(config, R.layout.player_proxy) {
    private val subscription = CompositeSubscription()

    private var child: MediaView? = null

    init {
        showBusyIndicator()
    }

    internal fun setChild(child: MediaView) {
        removeChildView()
        hideBusyIndicator()

        setChildView(child)

        bootupChild()

        // forward double clicks
        child.tapListener = ForwardingTapListener()
        subscription.add(child.viewed().subscribe { this.onMediaShown() })

        // forward controller view
        subscription.add(child.controllerView().subscribe { this.publishControllerView(it) })
    }

    /**
     * Adds the proxied child above the preview.
     */
    private fun setChildView(mediaView: MediaView) {
        var idx = childCount
        val previewView = previewView
        if (previewView != null && previewView.parent === this) {
            idx = indexOfChild(previewView) + 1
        }

        // transfer the layout parameters
        mediaView.layoutParams = layoutParams
        mediaView.viewAspect = viewAspect
        addView(mediaView, idx)

        child = mediaView
    }

    private fun removeChildView() {
        if (child == null)
            return

        subscription.clear()

        teardownChild()
        removeView(child)

        child = null
    }

    private fun bootupChild() {
        if (child != null) {
            if (isPlaying)
                child!!.playMedia()
        }
    }

    private fun teardownChild() {
        child?.let { child ->
            if (isPlaying)
                child.stopMedia()
        }
    }

    override fun playMedia() {
        super.playMedia()
        child?.playMedia()
    }

    override fun stopMedia() {
        super.stopMedia()
        child?.stopMedia()
    }

    override fun rewind() {
        child?.rewind()
    }

    override val actualMediaView: MediaView
        get() = child ?: this

    override fun onMediaShown() {
        viewAspect = -1f
        removePreviewImage()
        super.onMediaShown()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        child?.let { child ->
            event.offsetLocation(
                    (child.paddingLeft - paddingLeft).toFloat(),
                    (child.paddingTop - paddingTop).toFloat())

            return child.onTouchEvent(event)
        }

        return super.onTouchEvent(event)
    }

    private inner class ForwardingTapListener : MediaView.TapListener {
        override fun onSingleTap(event: MotionEvent): Boolean {
            return tapListener?.onSingleTap(event) ?: false
        }

        override fun onDoubleTap(event: MotionEvent): Boolean {
            return tapListener?.onDoubleTap(event) ?: false
        }
    }
}
