package com.pr0gramm.app.ui.views.viewer

import android.annotation.SuppressLint
import android.net.Uri
import com.github.salomonbrys.kodein.instance
import com.pr0gramm.app.services.gif.GifToVideoService
import com.pr0gramm.app.util.AndroidUtility.checkMainThread
import org.slf4j.LoggerFactory
import rx.Observable

/**
 */
@SuppressLint("ViewConstructor")
class Gif2VideoMediaView internal constructor(config: MediaView.Config) : ProxyMediaView(config) {
    private val gifToVideoService: GifToVideoService = instance()

    init {
        startWebmConversion()
    }

    private fun startWebmConversion() {
        logger.info("Start converting gif to webm")

        // normalize to http://
        val gifUrl = mediaUri.toString().replace("https://", "http://")

        // and start conversion!
        gifToVideoService.toVideo(gifUrl)
                .compose(backgroundBindView())
                .onErrorResumeNext(Observable.just(GifToVideoService.Result(gifUrl)))
                .limit(1)
                .doAfterTerminate { this.hideBusyIndicator() }
                .subscribe { this.handleConversionResult(it) }
    }

    private fun handleConversionResult(result: GifToVideoService.Result) {
        checkMainThread()

        // create the correct child-viewer
        val mediaView: MediaView
        if (result.videoUrl != null) {
            logger.info("Converted successfully, replace with video player")
            val videoUri = Uri.parse(result.videoUrl)
            val webm = mediaUri.withUri(videoUri, MediaUri.MediaType.VIDEO)
            mediaView = MediaViews.newInstance(config.copy(mediaUri = webm))

        } else {
            logger.info("Conversion did not work, showing gif")
            mediaView = GifMediaView(config)
        }

        mediaView.removePreviewImage()
        setChild(mediaView)
    }

    companion object {
        private val logger = LoggerFactory.getLogger("Gif2VideoMediaView")
    }
}
