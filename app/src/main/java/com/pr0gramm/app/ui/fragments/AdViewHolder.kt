package com.pr0gramm.app.ui.fragments

import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.ui.AdService
import com.pr0gramm.app.ui.base.whileIsAttachedScope
import com.pr0gramm.app.util.BrowserHelper
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.dp
import com.pr0gramm.app.util.trace
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class AdViewHolder private constructor(val adView: AdManagerAdView, itemView: View) :
    RecyclerView.ViewHolder(itemView) {

    companion object {
        private val logger = Logger("AdViewHolder")

        fun new(context: Context): AdViewHolder {
            trace { "newContainerView" }

            val container = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    context.dp(70f).roundToInt(),
                )
            }

            trace { "newPlaceholderView" }
            val placeholder = ImageView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )

                scaleType = ImageView.ScaleType.CENTER_CROP

                if (context.injector.instance<AdService>().isSideloaded()) {
                    setImageDrawable(
                        VectorDrawableCompat.create(
                            resources, R.drawable.pr0mium_ad, null
                        )
                    )
                }

                setOnClickListener {
                    BrowserHelper.openCustomTab(context, Uri.parse("https://pr0gramm.com/pr0mium"))
                }
            }

            container.addView(placeholder)

            val adService = context.injector.instance<AdService>()

            // create a new ad view with the right size
            trace { "newAdView()" }
            val adView = adService.newAdView(context)

            var previousWidth = 0
            var currentJob = Job()

            container.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                trace { "AdContainer was layouted, width is ${container.width}" }
                if (previousWidth == container.width) {
                    return@addOnLayoutChangeListener
                }

                previousWidth = container.width

                // cancel previous job and create a new one for the new load request
                currentJob.cancel()
                currentJob = Job()

                container.whileIsAttachedScope {
                    withContext(currentJob) {
                        trace { "Loading ad now." }
                        adView.setAdSize(AdSize.BANNER)

                        adService.load(adView, Config.AdType.FEED).collect { state ->
                            this@Companion.trace { "adStateChanged($state)" }

                            if (state == AdService.AdLoadState.SUCCESS && adView.parent == null) {
                                if (adView.parent !== placeholder) {
                                    logger.info { "Ad was loaded, showing ad now." }
                                    container.removeView(placeholder)
                                    container.addView(adView)
                                }
                            }

                            if (state == AdService.AdLoadState.CLOSED || state == AdService.AdLoadState.FAILURE) {
                                if (placeholder.parent !== container) {
                                    logger.info { "Ad not loaded: $state" }
                                    container.removeView(adView)
                                    container.addView(placeholder)
                                }
                            }
                        }
                    }
                }
            }

            return AdViewHolder(adView, container)
        }
    }
}
