package com.pr0gramm.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
import com.google.android.gms.ads.MobileAds
import com.pr0gramm.app.Logger
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.util.di.injector

class InterstitialAdler(context: Context) {
    private val logger = Logger("InterstitialAdler")
    private val adService: AdService = context.injector.instance()
    private val handler = Handler(Looper.getMainLooper())

    private val ad: InterstitialAd? = adService.buildInterstitialAd(context)

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
        ad.adListener = object : AdService.TrackingAdListener("i") {
            override fun onAdOpened() {
                handler.removeCallbacks(onAdNotShown)
                ad.adListener = reloadAdListener

                super.onAdOpened()
                block()
            }
        }

        // again, apply muted, just to be on the sure side
        MobileAds.setAppVolume(0f)
        MobileAds.setAppMuted(true)

        ad.show()
    }

    private fun reload() {
        ad?.loadAd(AdRequest.Builder().build())
    }

    private val reloadAdListener = object : AdService.TrackingAdListener("i") {
        override fun onAdClosed() {
            super.onAdClosed()
            reload()
        }
    }
}
