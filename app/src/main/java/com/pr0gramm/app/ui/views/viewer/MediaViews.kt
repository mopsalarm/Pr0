package com.pr0gramm.app.ui.views.viewer

import com.pr0gramm.app.Settings
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
    @JvmStatic
    fun newInstance(config_: MediaView.Config): MediaView {
        var config = config_

        val settings = Settings.get()

        if (config.audio && settings.disableAudio)
            config = config.copy(audio = false)

        // handle delay urls first.
        if (config.mediaUri.delay) {
            return DelayedMediaView(config.copy(mediaUri = config.mediaUri.copy(delay = false)))
        }

        val uri = config.mediaUri

        return if (uri.mediaType == MediaUri.MediaType.VIDEO) {
            VideoMediaView(config)

        } else if (uri.mediaType == MediaUri.MediaType.GIF) {
            if (shouldUseGifToWebm(uri, settings)) {
                Gif2VideoMediaView(config)
            } else {
                GifMediaView(config)
            }

        } else {
            ImageMediaView(config)
        }
    }


    private fun shouldUseGifToWebm(uri: MediaUri, settings: Settings): Boolean {
        return !uri.isLocal && settings.convertGifToWebm
    }

    @JvmStatic
    fun adaptFragmentLifecycle(lifecycle: Observable<FragmentEvent>, view: MediaView) {
        lifecycle.compose(bindView(view)).subscribe { event ->
            when (event) {
                FragmentEvent.RESUME -> view.onResume()
                FragmentEvent.PAUSE -> view.onPause()
            }
        }
    }
}
