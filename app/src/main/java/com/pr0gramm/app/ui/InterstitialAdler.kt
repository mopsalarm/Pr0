package com.pr0gramm.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
import com.pr0gramm.app.Logger
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.util.di.injector

class InterstitialAdler(context: Context) {
    private val logger = Logger("InterstitialAdler")
    private val adService: AdService = context.injector.instance()
    private val handler = Handler(Looper.getMainLooper())

    private val ad: InterstitialAd? = if (adService.enabledForTypeNow(Config.AdType.FEED_TO_POST_INTERSTITIAL)) {
        InterstitialAd(context).also { ad ->
            ad.adUnitId = AdService.interstitialUnitId
            ad.adListener = TrackingAdListener()
            ad.setImmersiveMode(false)
            ad.loadAd(AdRequest.Builder().build())
        }
    } else {
        null
    }

    fun runWithAd(block: () -> Unit) {
        logger.debug { "Ad is loaded: ${ad?.isLoaded}" }

        val ad = this.ad ?: return block()
        if (!ad.isLoaded || !adService.shouldShowInterstitialAd()) {
            logger.debug { "Ad is not loaded or not activated." }
            return block()
        }

        adService.interstitialAdWasShown()

        // schedule a handler in case the ad can not be shown
        val onAdNotShown = Runnable {
            logger.warn { "Watchdog timer hit, ad was not shown after calling 'show'." }
            Track.adEvent("i_watchdog")
            block()
        }

        // set watchdog timer for 1 second.
        handler.postDelayed(onAdNotShown, 1000)

        // show app
        ad.adListener = object : TrackingAdListener() {
            override fun onAdOpened() {
                super.onAdOpened()

                handler.removeCallbacks(onAdNotShown)

                ad.adListener = reloadAdListener
                block()
            }
        }

        ad.show()
    }

    private fun reload() {
        ad?.loadAd(AdRequest.Builder().build())
    }

    private val reloadAdListener = object : TrackingAdListener() {
        override fun onAdClosed() {
            reload()
        }
    }

    private open class TrackingAdListener : AdListener() {
        override fun onAdFailedToLoad(p0: Int) {
            Track.adEvent("i_failed_to_load")
        }

        override fun onAdLeftApplication() {
            Track.adEvent("i_left_application")
        }

        override fun onAdImpression() {
            Track.adEvent("i_impression")
        }

        override fun onAdOpened() {
            Track.adEvent("i_opened")
        }
    }
}
