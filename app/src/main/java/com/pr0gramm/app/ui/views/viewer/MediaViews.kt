package com.pr0gramm.app.ui.views.viewer

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
            SimpleVideoMediaView(config)

        } else if (uri.mediaType == MediaUri.MediaType.GIF) {
            GifMediaView(config)

        } else {
            ImageMediaView(config)
        }
    }
}
