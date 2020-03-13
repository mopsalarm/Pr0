package com.pr0gramm.app.ui

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.util.di.injector

class InterstitialAdler(context: Context) {
    private val adService: AdService = context.injector.instance()

    private val ad: InterstitialAd? = if (adService.enabledForTypeNow(Config.AdType.FEED_TO_POST_INTERSTITIAL)) {
        InterstitialAd(context).also { ad ->
            ad.adUnitId = "ca-app-pub-3940256099942544/1033173712"
            ad.loadAd(AdRequest.Builder().build())
        }
    } else {
        null
    }

    fun runWithAd(block: () -> Unit) {
        val ad = this.ad ?: return block()

        if (!ad.isLoaded || !adService.enabledForTypeNow(Config.AdType.FEED_TO_POST_INTERSTITIAL)) {
            return block()
        }

        ad.adListener = object : AdListener() {
            override fun onAdOpened() {
                ad.adListener = reloadAdListener
                block()
            }
        }

        ad.show()
    }

    private fun reload() {
        ad?.loadAd(AdRequest.Builder().build())
    }

    private val reloadAdListener = object : AdListener() {
        override fun onAdClosed() {
            reload()
        }
    }
}
