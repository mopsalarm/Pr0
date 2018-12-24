package com.pr0gramm.app.ui.fragments

import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.pr0gramm.app.R
import com.pr0gramm.app.services.config.Config
import com.pr0gramm.app.ui.AdService
import com.pr0gramm.app.ui.dialogs.ignoreError
import com.pr0gramm.app.util.*
import com.trello.rxlifecycle.android.RxLifecycleAndroid
import org.kodein.di.erased.instance
import kotlin.math.roundToInt

class AdViewHolder private constructor(val adView: AdView, itemView: View) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {

    companion object {
        private val logger = Logger("AdViewHolder")

        fun new(context: Context): AdViewHolder {
            trace { "newContainerView" }
            val container = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            trace { "newPlaceholderView" }
            val placeholder = ImageView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        context.dip2px(70f).roundToInt())

                scaleType = ImageView.ScaleType.CENTER_CROP

                setImageDrawable(VectorDrawableCompat.create(
                        resources, R.drawable.pr0mium_ad, null))

                setOnClickListener {
                    BrowserHelper.openCustomTab(context, Uri.parse("https://pr0gramm.com/pr0mium"))
                }
            }

            container.addView(placeholder)

            val adService = context.directKodein.instance<AdService>()

            trace { "newAdView()" }
            val adView = adService.newAdView(context).apply {
                adSize = AdSize(AdSize.FULL_WIDTH, 70)
            }

            logger.info { "Starting loading ad now." }

            // now load the ad and show it, once it finishes loading
            adService.load(adView, Config.AdType.FEED)
                    .subscribeOnBackground()
                    .observeOnMainThread()
                    .ignoreError()
                    .debug("AdService.load")
                    .compose(RxLifecycleAndroid.bindView(container))
                    .subscribe { state ->
                        trace { "adStateChanged($state)" }
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

            return AdViewHolder(adView, container)
        }
    }
}
