package com.pr0gramm.app.ui.views.viewer

import com.trello.rxlifecycle.android.FragmentEvent
import com.trello.rxlifecycle.android.RxLifecycleAndroid.bindView
import rx.Observable

/**
 * This class provides static methods to create a new
 * [com.pr0gramm.app.ui.views.viewer.MediaView] for some url.
 */
object MediaViews {

    /**
     * Instantiates one of the viewer subclasses depending on the provided configuration.

     * @return A new [MediaView] instance.
     */
    fun newInstance(config: MediaView.Config): MediaView {
        // handle delay urls first.
        if (config.mediaUri.delay) {
            return DelayedMediaView(config.copy(mediaUri = config.mediaUri.copy(delay = false)))
        }

        val uri = config.mediaUri

        return if (uri.mediaType == MediaUri.MediaType.VIDEO) {
            VideoMediaView(config)

        } else if (uri.mediaType == MediaUri.MediaType.GIF) {
            GifMediaView(config)

        } else {
            ImageMediaView(config)
        }
    }

    fun adaptFragmentLifecycle(lifecycle: Observable<FragmentEvent>, view: MediaView) {
        lifecycle.compose(bindView(view)).subscribe { event ->
            when (event) {
                FragmentEvent.RESUME -> view.onResume()
                FragmentEvent.PAUSE -> view.onPause()
                else -> Unit
            }
        }
    }
}
