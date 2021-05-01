package com.pr0gramm.app.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.pr0gramm.app.Logger
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.util.Holder
import com.pr0gramm.app.util.di.injector

class InterstitialAdler(private val activity: Activity) {
    private val logger = Logger("InterstitialAdler")
    private val adService: AdService = activity.injector.instance()
    private val handler = Handler(Looper.getMainLooper())

    private var holder: Holder<InterstitialAd?> = adService.buildInterstitialAd(activity)

    private val ad: InterstitialAd?
        get() = holder.valueOrNull

    fun runWithAd(block: () -> Unit) {
        val ad = this.ad ?: return block()
        if (!adService.shouldShowInterstitialAd()) {
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
        ad.setImmersiveMode(false)

        // again, apply muted, just to be on the sure side
        MobileAds.setAppVolume(0f)
        MobileAds.setAppMuted(true)

        ad.show(activity)

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                handler.removeCallbacks(onAdNotShown)
                block()

                // load a new ad
                holder = adService.buildInterstitialAd(activity)
            }
        }

        // always run block
        block()
    }
}
