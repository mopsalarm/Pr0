package com.pr0gramm.app.ui

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.pr0gramm.app.*
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.ignoreAllExceptions
import com.pr0gramm.app.util.observeOnMainThread
import rx.Observable
import rx.subjects.ReplaySubject
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
    fun load(view: AdView?, type: Config.AdType): Observable<AdLoadState> {
        if (view == null) {
            return Observable.empty()
        }

        val listener = RxAdListener()
        view.adListener = listener
        view.loadAd(AdRequest.Builder().build())
        return listener.loadedSubject
    }

    fun enabledForTypeNow(type: Config.AdType): Boolean {
        if (Settings.get().alwaysShowAds) {
            // If the user opted in to ads, we always show the feed ad.
            return true
        }

        // do not show ads for premium users
        return !userService.userIsPremium && type in configService.config().adTypes
    }

    fun enabledForType(type: Config.AdType): Observable<Boolean> {
        return userService.loginStates
                .observeOnMainThread(firstIsSync = true)
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

    companion object {
        private val logger = Logger("AdService")

        val interstitialUnitId: String = if (BuildConfig.DEBUG) {
            "ca-app-pub-3940256099942544/1033173712"
        } else {
            "ca-app-pub-2308657767126505/4231980510"
        }

        val bannerUnitId: String = if (BuildConfig.DEBUG) {
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

    private class RxAdListener : AdListener() {
        val loadedSubject = ReplaySubject.create<AdLoadState>().toSerialized()!!

        override fun onAdLeftApplication() {
        }

        override fun onAdLoaded() {
            loadedSubject.onNext(AdLoadState.SUCCESS)
        }

        override fun onAdFailedToLoad(i: Int) {
            loadedSubject.onNext(AdLoadState.FAILURE)
        }

        override fun onAdClosed() {
            loadedSubject.onNext(AdLoadState.CLOSED)
        }
    }

    enum class AdLoadState {
        SUCCESS, FAILURE, CLOSED
    }
}
