package com.pr0gramm.app.ui

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.observeOnMainThread
import rx.Observable
import rx.subjects.ReplaySubject


/**
 * Utility methods for ads.
 */

class AdService(private val configService: ConfigService, private val userService: UserService) {

    /**
     * Loads an ad into this view. This method also registers a listener to track the view.
     * The resulting completable completes once the ad finishes loading.
     */
    fun load(view: AdView?, type: Config.AdType): Observable<AdLoadState> {
        if (view == null) {
            return Observable.empty()
        }

        // we want to have tracking and information about the ad loading.
        val listener = TrackingAdListener(type)
        view.adListener = listener

        view.loadAd(AdRequest.Builder().build())

        return listener.loadedSubject
    }

    fun enabledForTypeNow(type: Config.AdType): Boolean {
        if (Settings.get().alwaysShowAds) {
            // If the user opted in to ads, we always show the feed ad.
            return type == Config.AdType.FEED
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

    fun newAdView(context: Context): AdView {
        val view = AdView(context.applicationContext)

        if (BuildConfig.DEBUG) {
            view.adUnitId = "ca-app-pub-3940256099942544/6300978111"
        } else {
            view.adUnitId = context.getString(R.string.banner_ad_unit_id)
        }

        val backgroundColor = AndroidUtility.resolveColorAttribute(context, android.R.attr.windowBackground)
        view.setBackgroundColor(backgroundColor)

        return view
    }

    private class TrackingAdListener internal constructor(private val adType: Config.AdType) : AdListener() {
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
