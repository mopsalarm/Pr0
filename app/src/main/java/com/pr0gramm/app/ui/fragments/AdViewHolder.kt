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
import com.google.android.gms.ads.AdView
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.ui.AdService
import com.pr0gramm.app.ui.base.onAttachedScope
import com.pr0gramm.app.util.BrowserHelper
import com.pr0gramm.app.util.delay
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.dp
import com.pr0gramm.app.util.trace
import kotlin.math.roundToInt

class AdViewHolder private constructor(val adView: AdView, itemView: View) :
    RecyclerView.ViewHolder(itemView) {

    companion object {
        private val logger = Logger("AdViewHolder")

        fun new(context: Context): AdViewHolder {
            trace { "newContainerView" }

            val container = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            trace { "newPlaceholderView" }
            val placeholder = ImageView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    context.dp(70f).roundToInt()
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

            trace { "newAdView()" }
            val adView = adService.newAdView(context).apply {
                setAdSize(AdSize(AdSize.FULL_WIDTH, 70))
            }

            container.onAttachedScope {
                trace { "AdContainer was attached." }

                delay(Duration.seconds(1))

                trace { "Loading ad now." }

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

            return AdViewHolder(adView, container)
        }
    }
}
