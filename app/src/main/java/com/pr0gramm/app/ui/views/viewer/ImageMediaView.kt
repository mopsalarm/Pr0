package com.pr0gramm.app.ui.views.viewer

import android.annotation.SuppressLint
import android.view.View
import androidx.core.view.isVisible
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.pr0gramm.app.R
import com.pr0gramm.app.databinding.PlayerKindImageBinding
import com.pr0gramm.app.util.ErrorFormatting
import com.pr0gramm.app.util.debugOnly
import com.pr0gramm.app.util.decoders.Decoders
import com.pr0gramm.app.util.decoders.PicassoDecoder
import com.pr0gramm.app.util.di.injector
import kotlin.math.max

@SuppressLint("ViewConstructor")
class ImageMediaView(config: MediaView.Config) : MediaView(config, R.layout.player_kind_image) {
    private val capImageRatio = 1f / 64f

    private val tag = "ImageMediaView" + System.identityHashCode(this)

    private val views = PlayerKindImageBinding.bind(this)

    init {
        views.image.visibility = View.VISIBLE
        views.image.alpha = 0f
        views.image.isZoomEnabled = false
        views.image.isQuickScaleEnabled = false
        views.image.isPanEnabled = false

        debugOnly {
            views.image.setDebug(true)
        }

        // try not to use too much memory, even on big devices
        views.image.setMaxTileSize(2048)

        views.image.setBitmapDecoderFactory(PicassoDecoder.factory(tag, picasso))
        views.image.setRegionDecoderFactory(Decoders.regionDecoderFactory(context.injector.instance()))

        views.image.setOnImageEventListener(object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
            override fun onImageLoaded() {
                hideBusyIndicator()
                onMediaShown()
            }

            override fun onImageLoadError(error: Exception) {
                hideBusyIndicator()
                showErrorIndicator(error)
            }

            override fun onReady() {
                applyScaling()
            }
        })

        views.image.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (views.image.sWidth > 0 && views.image.sHeight > 0) {
                applyScaling()
            }
        }

//        addOnDetachListener {
//            picasso.cancelTag(tag)
//            views.image.recycle()
//            views.image.setOnImageEventListener(null)
//            views.image.removeFromParent()
//        }

        // start loading
        val tiling = config.previewInfo?.let { it.width > 2000 || it.height > 2000 } ?: false
        views.image.setImage(ImageSource.uri(effectiveUri).tiling(tiling))

        showBusyIndicator()
    }

    internal fun applyScaling() {
        val ratio = views.image.sWidth.toFloat() / views.image.sHeight.toFloat()
        val ratioCapped = max(ratio, capImageRatio)

        viewAspect = ratioCapped

        val viewWidth = views.image.width.toFloat()
        val viewHeight = views.image.height.toFloat()

        val maxScale = viewWidth / views.image.sWidth
        val minScale = viewHeight / views.image.sHeight

        views.image.minScale = minScale
        views.image.maxScale = maxScale
        views.image.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_START)
        views.image.resetScaleAndCenter()
    }

    override fun onMediaShown() {
        views.image.isVisible = true

        if (views.image.alpha == 0f) {
            views.image.animate().alpha(1f)
                    .setDuration(MediaView.ANIMATION_DURATION)
                    .withEndAction { super.onMediaShown() }
                    .start()
        } else {
            super.onMediaShown()
        }
    }

    @SuppressLint("SetTextI18n")
    internal fun showErrorIndicator(error: Exception) {
        views.error.visibility = View.VISIBLE
        views.error.alpha = 0f
        views.error.animate().alpha(1f).start()

        // set a more useful error message
        views.error.text = context.getText(R.string.could_not_load_image).toString() +
                "\n\n" + ErrorFormatting.getFormatter(error).getMessage(context, error)
    }
}
