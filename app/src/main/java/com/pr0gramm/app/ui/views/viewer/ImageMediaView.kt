package com.pr0gramm.app.ui.views.viewer

import android.annotation.SuppressLint
import android.view.View
import android.widget.TextView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.views.instance
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.decoders.Decoders
import com.pr0gramm.app.util.decoders.PicassoDecoder
import com.squareup.picasso.Downloader
import kotterknife.bindView

@SuppressLint("ViewConstructor")
class ImageMediaView(config: MediaView.Config) : MediaView(config, R.layout.player_kind_image) {
    private val CAP_IMAGE_RATIO = 1f / 30f

    private val tag = "ImageMediaView" + System.identityHashCode(this)
    private val zoomView = findViewById<View>(R.id.tabletlayout) != null

    private val imageView: SubsamplingScaleImageView by bindView(R.id.image)
    private val errorIndicator: TextView by bindView(R.id.error)

    private val downloader: Downloader by instance()

    init {
        imageView.visibility = View.VISIBLE
        imageView.alpha = 0f
        imageView.isZoomEnabled = false
        imageView.isQuickScaleEnabled = false
        imageView.isPanEnabled = false

        debug {
            imageView.setDebug(true)
        }

        // try not to use too much memory, even on big devices
        imageView.setMaxTileSize(2048)

        imageView.setBitmapDecoderFactory { PicassoDecoder(tag, picasso) }
        imageView.setRegionDecoderFactory { Decoders.newImageDecoder(downloader) }

        imageView.setOnImageEventListener(object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
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

        imageView.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (imageView.sWidth > 0 && imageView.sHeight > 0) {
                applyScaling()
            }
        }

        addOnDetachListener {
            picasso.cancelTag(tag)
            imageView.recycle()
            imageView.setOnImageEventListener(null)
            imageView.removeFromParent()
        }

        // start loading
        val tiling = config.previewInfo?.let { it.width > 2000 || it.height > 2000 } ?: false
        imageView.setImage(ImageSource.uri(effectiveUri).tiling(tiling))

        showBusyIndicator()
    }

    internal fun applyScaling() {
        val ratio = imageView.sWidth.toFloat() / imageView.sHeight.toFloat()
        val ratioCapped = Math.max(ratio, CAP_IMAGE_RATIO)

        viewAspect = ratioCapped

        val viewWidth = imageView.width.toFloat()
        val viewHeight = imageView.height.toFloat()

        val maxScale = viewWidth / imageView.sWidth
        val minScale = viewHeight / imageView.sHeight

        imageView.minScale = minScale
        imageView.maxScale = maxScale
        imageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_START)
        imageView.resetScaleAndCenter()
    }

    override var viewAspect: Float
        get() = super.viewAspect
        set(viewAspect) {
            if (!zoomView) {
                super.viewAspect = viewAspect
            }
        }

    override fun onMediaShown() {
        imageView.visible = true

        if (imageView.alpha == 0f) {
            imageView.animate().alpha(1f)
                    .setDuration(MediaView.ANIMATION_DURATION)
                    .withEndAction { super.onMediaShown() }
                    .start()
        } else {
            super.onMediaShown()
        }
    }

    @SuppressLint("SetTextI18n")
    internal fun showErrorIndicator(error: Exception) {
        errorIndicator.visibility = View.VISIBLE
        errorIndicator.alpha = 0f
        errorIndicator.animate().alpha(1f).start()

        // set a more useful error message
        errorIndicator.text = context.getText(R.string.could_not_load_image).toString() +
                "\n\n" + ErrorFormatting.getFormatter(error).getMessage(context, error)
    }
}
