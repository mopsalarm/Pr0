package com.pr0gramm.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.Choreographer
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.pr0gramm.app.*
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.services.Track
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

    init {
        // track that we use ads.
        configService.observeConfig().subscribe { config -> Track.updateAdType(config.adType) }
    }

    private fun isEnabledFor(type: Config.AdType): Boolean {
        if (Settings.get().alwaysShowAds) {
            // If the user opted in to ads, we always show the feed ad.
            return type == Config.AdType.FEED
        }

        // do not show ads for premium users
        return !userService.userIsPremium && configService.config().adType == type
    }

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

        view.loadAd(AdRequest.Builder()
                .setTagForUnderAgeOfConsent(AdRequest.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE)
                .setMaxAdContentRating(AdRequest.MAX_AD_CONTENT_RATING_MA)
                .addTestDevice("5436541A8134C1A32DACFD10442A32A1") // pixel
                .build())

        return listener.loadedSubject
    }

    fun enabledForType(type: Config.AdType): Observable<Boolean> {
        return userService.loginStates
                .observeOnMainThread(firstIsSync = true)
                .map { isEnabledFor(type) }
                .distinctUntilChanged()
    }

    fun newAdView(context: Context): AdView {
        val view = AdView(context.applicationContext)

        if(BuildConfig.DEBUG) {
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

object AdMobWorkaround {
    private val log = Logger("AdModWorkaround")

    private const val CALLBACK_ANIMATION = 1
    private var choreographerAnimationCallback: Any? = null

    private fun pauseAnimationCallbacks() {
        log.info { "Pausing animation callbacks" }
        try {
            val choreographerType = Class.forName("android.view.Choreographer")

            val mLock = choreographerType.getDeclaredField("mLock")
            mLock.isAccessible = true

            val mCallbackQueues = choreographerType.getDeclaredField("mCallbackQueues")
            mCallbackQueues.isAccessible = true

            val choreographer = Choreographer.getInstance()
            val callbackQueues = mCallbackQueues.get(choreographer) as? Array<*> ?: return

            val animationCallbackQueue = callbackQueues.getOrNull(CALLBACK_ANIMATION) ?: return
            val choreographerLock = mLock.get(choreographer)

            val callbackQueueType = animationCallbackQueue.javaClass

            val mHead = callbackQueueType.getDeclaredField("mHead")
            mHead.isAccessible = true

            synchronized(choreographerLock) {
                log.info { "Choreographer locked for pause, getting mHead" }

                val callback = mHead.get(animationCallbackQueue) ?: return
                log.info { "mHead=$callback" }

                mHead.set(animationCallbackQueue, null)
                log.info { "Cleared mHead" }

                choreographerAnimationCallback = callback
            }
        } catch (e: Throwable) {
            log.warn(e) { "Failed to fix Choreographer" }
        }
    }

    private fun resumeAnimationCallbacks() {
        val callback = choreographerAnimationCallback ?: return

        log.info { "Resuming animation callbacks" }
        try {
            val choreographerType = Class.forName("android.view.Choreographer")

            val mLock = choreographerType.getDeclaredField("mLock")
            mLock.isAccessible = true

            val mCallbackQueues = choreographerType.getDeclaredField("mCallbackQueues")
            mCallbackQueues.isAccessible = true

            val choreographer = Choreographer.getInstance()
            val callbackQueues = mCallbackQueues.get(choreographer) as? Array<*> ?: return

            val animationCallbackQueue = callbackQueues.getOrNull(CALLBACK_ANIMATION) ?: return
            val choreographerLock = mLock.get(choreographer)

            val callbackQueueType = animationCallbackQueue.javaClass

            val mHead = callbackQueueType.getDeclaredField("mHead")
            mHead.isAccessible = true

            synchronized(choreographerLock) {
                log.info { "Choreographer locked for restore" }

                var ptr = mHead.get(animationCallbackQueue)
                log.info { "Choreographer mHead=$ptr" }

                if (ptr == null) {
                    log.info { "Setting head to $callback" }
                    mHead.set(animationCallbackQueue, callback)
                } else {
                    val callbackRecordType = ptr.javaClass

                    val nextField = callbackRecordType.getDeclaredField("next")
                    nextField.isAccessible = true

                    while (true) {
                        val next = nextField.get(ptr)
                        if (next == null) {
                            log.info { "Setting next to $callback" }
                            nextField.set(ptr, callback)
                            break
                        }

                        ptr = next
                    }
                }

                choreographerAnimationCallback = null
            }
        } catch (e: Throwable) {
            log.warn(e) { "Failed to resume Choreographer animation callbacks" }
        }
    }

    fun install(app: ApplicationClass) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            return

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var openActivityCount: Int = 0

            @SuppressLint("NewApi")
            override fun onActivityStarted(activity: Activity?) {
                if (--openActivityCount == 0) {
                    pauseAnimationCallbacks()
                }
            }

            @SuppressLint("NewApi")
            override fun onActivityStopped(activity: Activity?) {
                if (openActivityCount++ == 0) {
                    resumeAnimationCallbacks()
                }
            }

            override fun onActivityPaused(activity: Activity?) {
            }

            override fun onActivityResumed(activity: Activity?) {
            }

            override fun onActivityDestroyed(activity: Activity?) {
            }

            override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {
            }

            override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
            }

        })
    }
}

