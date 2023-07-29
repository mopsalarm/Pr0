package com.pr0gramm.app.ui

import android.app.Application
import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.Logger
import com.pr0gramm.app.Settings
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.Holder
import com.pr0gramm.app.util.ignoreAllExceptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit


/**
 * Utility methods for ads.
 */

class AdService(
    private val appContext: Application,
    private val configService: ConfigService,
    private val userService: UserService,
) {

    private val logger = Logger("AdService")
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
                    this@channelFlow.trySend(AdLoadState.SUCCESS)
                }

                override fun onAdFailedToLoad(p0: LoadAdError) {
                    super.onAdFailedToLoad(p0)
                    this@channelFlow.trySend(AdLoadState.FAILURE)
                }

                override fun onAdClosed() {
                    super.onAdClosed()
                    this@channelFlow.trySend(AdLoadState.CLOSED)
                }
            }

            view.loadAd(AdRequest.Builder().build())

            awaitClose()
        }
    }

    private fun enabledForTypeNow(type: Config.AdType): Boolean {
        if (Settings.alwaysShowAds) {
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
        val view = AdView(context)
        view.adUnitId = bannerUnitId

        val backgroundColor = AndroidUtility.resolveColorAttribute(context, android.R.attr.windowBackground)
        view.setBackgroundColor(backgroundColor)

        return view
    }

    fun buildInterstitialAd(context: Context): Holder<InterstitialAd?> {
        return if (enabledForTypeNow(Config.AdType.FEED_TO_POST_INTERSTITIAL)) {
            val value = CompletableDeferred<InterstitialAd?>()

            InterstitialAd.load(
                context,
                interstitialUnitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(p0: InterstitialAd) {
                        value.complete(p0)
                    }

                    override fun onAdFailedToLoad(p0: LoadAdError) {
                        value.complete(null)
                    }
                })

            Holder { value.await() }
        } else {
            return Holder { null }
        }
    }

    fun isSideloaded(): Boolean {
        try {
            val name = appContext
                .packageManager
                .getInstallerPackageName(BuildConfig.APPLICATION_ID)

            logger.info { "Installer package is '$name'" }

            return name.isNullOrBlank() || name == "com.android.packageinstaller"

        } catch (err: Exception) {
            logger.warn(err) { "Failed to query for installer package" }

            // hm. better be safe and assume not sideloaded
            return false
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
            // for some reason an internal getVersionString returns null,
            // and the result is not checked. We ignore the error in that case
            ignoreAllExceptions {
                val listener = OnInitializationCompleteListener { }
                MobileAds.initialize(context, listener)

                MobileAds.setAppVolume(0f)
                MobileAds.setAppMuted(true)
            }
        }
    }

    enum class AdLoadState {
        SUCCESS, FAILURE, CLOSED
    }

    open class TrackingAdListener(private val prefix: String) : AdListener() {
        override fun onAdImpression() {
            Track.adEvent("${prefix}_impression")
        }

        override fun onAdOpened() {
            Track.adEvent("${prefix}_opened")
        }
    }

}
