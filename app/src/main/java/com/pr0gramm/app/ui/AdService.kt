package com.pr0gramm.app.ui

import android.content.Context
import com.google.android.gms.ads.*
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.Settings
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.ignoreAllExceptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit


/**
 * Utility methods for ads.
 */

class AdService(private val configService: ConfigService, private val userService: UserService) {
    private var lastInterstitialAdShown: Instant? = null

    /**
     * Loads an ad into this view. This method also registers a listener to track the view.
     * The resulting completable completes once the ad finishes loading.
     */
    fun load(view: AdView?, type: Config.AdType): Flow<AdLoadState> {
        if (view == null) {
            return emptyFlow()
        }

        return channelFlow {
            view.adListener = object : TrackingAdListener("b") {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    if (!isClosedForSend) {
                        offer(AdLoadState.SUCCESS)
                    }
                }

                override fun onAdFailedToLoad(p0: Int) {
                    super.onAdFailedToLoad(p0)
                    if (!isClosedForSend) {
                        offer(AdLoadState.FAILURE)
                    }
                }

                override fun onAdClosed() {
                    super.onAdClosed()
                    if (!isClosedForSend) {
                        offer(AdLoadState.CLOSED)
                    }
                }
            }

            view.loadAd(AdRequest.Builder().build())

            awaitClose()
        }
    }

    fun enabledForTypeNow(type: Config.AdType): Boolean {
        if (Settings.get().alwaysShowAds) {
            // If the user opted in to ads, we always show the feed ad.
            return type == Config.AdType.FEED
        }

        if (userService.userIsPremium) {
            return false
        }

        if (userService.isAuthorized) {
            return type in configService.config().adTypesLoggedIn
        } else {
            return type in configService.config().adTypesLoggedOut
        }
    }

    fun enabledForType(type: Config.AdType): Flow<Boolean> {
        return userService.loginStates
                .map { enabledForTypeNow(type) }
                .distinctUntilChanged()
    }

    fun shouldShowInterstitialAd(): Boolean {
        if (!enabledForTypeNow(Config.AdType.FEED_TO_POST_INTERSTITIAL)) {
            return false
        }

        val lastAdShown = lastInterstitialAdShown ?: return true
        val interval = configService.config().interstitialAdIntervalInSeconds
        return Duration.since(lastAdShown).convertTo(TimeUnit.SECONDS) > interval
    }

    fun interstitialAdWasShown() {
        this.lastInterstitialAdShown = Instant.now()
    }

    fun newAdView(context: Context): AdView {
        val view = AdView(context.applicationContext)
        view.adUnitId = bannerUnitId

        val backgroundColor = AndroidUtility.resolveColorAttribute(context, android.R.attr.windowBackground)
        view.setBackgroundColor(backgroundColor)

        return view
    }

    fun buildInterstitialAd(context: Context): InterstitialAd? {
        return if (enabledForTypeNow(Config.AdType.FEED_TO_POST_INTERSTITIAL)) {
            InterstitialAd(context).also { ad ->
                ad.adUnitId = interstitialUnitId
                ad.adListener = TrackingAdListener("i")
                ad.setImmersiveMode(false)
                ad.loadAd(AdRequest.Builder().build())
            }
        } else {
            null
        }
    }

    companion object {
        private val interstitialUnitId: String = if (BuildConfig.DEBUG) {
            "ca-app-pub-3940256099942544/1033173712"
        } else {
            "ca-app-pub-2308657767126505/4231980510"
        }

        private val bannerUnitId: String = if (BuildConfig.DEBUG) {
            "ca-app-pub-3940256099942544/6300978111"
        } else {
            "ca-app-pub-2308657767126505/5614778874"
        }

        fun initializeMobileAds(context: Context) {
            val appId = if (BuildConfig.DEBUG) {
                "ca-app-pub-3940256099942544~3347511713"
            } else {
                "ca-app-pub-2308657767126505~4138045673"
            }

            // for some reason an internal getVersionString returns null,
            // and the result is not checked. We ignore the error in that case
            ignoreAllExceptions {
                MobileAds.initialize(context, appId)

                MobileAds.setAppVolume(0f)
                MobileAds.setAppMuted(true)
            }
        }
    }

    enum class AdLoadState {
        SUCCESS, FAILURE, CLOSED
    }

    open class TrackingAdListener(private val prefix: String) : AdListener() {
        override fun onAdFailedToLoad(p0: Int) {
            Track.adEvent("${prefix}_failed_to_load")
        }

        override fun onAdLeftApplication() {
            Track.adEvent("${prefix}_left_application")
        }

        override fun onAdImpression() {
            Track.adEvent("${prefix}_impression")
        }

        override fun onAdOpened() {
            Track.adEvent("${prefix}_opened")
        }
    }

}
