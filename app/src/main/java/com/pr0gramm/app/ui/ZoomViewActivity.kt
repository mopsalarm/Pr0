package com.pr0gramm.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.View.SYSTEM_UI_FLAG_IMMERSIVE
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.core.view.isVisible
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.parcel.getExtraParcelableOrThrow
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.util.AndroidUtility.getTintedDrawable
import com.pr0gramm.app.util.decoders.Decoders
import com.pr0gramm.app.util.decoders.PicassoDecoder
import com.pr0gramm.app.util.di.instance
import com.squareup.picasso.Picasso
import kotterknife.bindView
import kotlin.math.max
import kotlin.math.min

class ZoomViewActivity : BaseAppCompatActivity("ZoomViewActivity") {
    private val tag = "ZoomViewActivity" + System.currentTimeMillis()

    internal val item: FeedItem by lazy {
        intent.getExtraParcelableOrThrow("ZoomViewActivity__item")
    }

    private val hq: ImageView by bindView(R.id.hq)

    private val busyIndicator: View by bindView(R.id.busy_indicator)
    private val imageView: SubsamplingScaleImageView by bindView(R.id.image)

    private val picasso: Picasso by instance()
    private val cache: Cache by instance()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.fullscreen)
        super.onCreate(savedInstanceState)

        // normal content view
        setContentView(R.layout.activity_zoom_view)

        Track.openZoomView(item.id)

        imageView.setMaxTileSize(2048)
        imageView.setDebug(BuildConfig.DEBUG)
        imageView.setBitmapDecoderFactory(PicassoDecoder.factory(tag, picasso))
        imageView.setRegionDecoderFactory(Decoders.regionDecoderFactory(cache))

        imageView.setOnImageEventListener(object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
            override fun onImageLoaded() {
                hideBusyIndicator()

                imageView.minScale = min(
                        0.5f * imageView.width.toFloat() / imageView.sWidth,
                        0.5f * imageView.height.toFloat() / imageView.sWidth)

                imageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CUSTOM)

                imageView.maxScale = max(
                        2 * imageView.width.toFloat() / imageView.sWidth,
                        2 * imageView.height.toFloat() / imageView.sWidth)
            }
        })


        if (Settings.loadHqInZoomView && isHqImageAvailable) {
            // dont show the hq button if we load the hq image directly
            hq.isVisible = false

            loadHqImage()
        } else {
            hq.setImageDrawable(getColoredHqIcon(R.color.grey_700))

            loadNormalImage()
        }

        imageView.setOnClickListener {
            toggleSystemUI()
        }

        // set the correct flags on startup
        showSystemUI()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (SYSTEM_UI_FLAG_IMMERSIVE
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    private fun showSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    private fun toggleSystemUI() {
        val immersive = window.decorView.systemUiVisibility and SYSTEM_UI_FLAG_IMMERSIVE == 0
        if (immersive) {
            hideSystemUI()
        } else {
            showSystemUI()
        }
    }

    override fun onDestroy() {
        picasso.cancelTag(tag)
        super.onDestroy()
    }

    private fun loadNormalImage() {
        val url = UriHelper.of(this).media(item)
        loadImageWithUrl(url)

        if (isHqImageAvailable) {
            hq.setOnClickListener { loadHqImage() }
            hq.animate().alpha(1f).start()
        } else {
            hq.isVisible = false
        }
    }

    private val isHqImageAvailable: Boolean
        get() = item.fullsize.isNotBlank()

    private fun loadHqImage() {
        if (hq.isVisible) {
            hq.setOnClickListener(null)
            hq.setImageDrawable(getColoredHqIcon(ThemeHelper.accentColor))
            hq.animate().alpha(1f).start()
        }

        val url = UriHelper.of(this).media(item, true)
        loadImageWithUrl(url)
    }

    private fun getColoredHqIcon(@ColorRes colorId: Int): Drawable {
        return getTintedDrawable(this, R.drawable.ic_action_high_quality, colorId)
    }

    private fun loadImageWithUrl(url: Uri) {
        showBusyIndicator()
        picasso.cancelTag(tag)
        imageView.setImage(ImageSource.uri(url))
    }

    private fun showBusyIndicator() {
        busyIndicator.isVisible = true
    }

    private fun hideBusyIndicator() {
        busyIndicator.isVisible = false
    }

    companion object {
        fun newIntent(context: Context, item: FeedItem): Intent {
            val intent = Intent(context, ZoomViewActivity::class.java)
            intent.putExtra("ZoomViewActivity__item", item)
            return intent
        }
    }
}
