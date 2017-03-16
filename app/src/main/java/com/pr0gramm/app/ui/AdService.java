package com.pr0gramm.app.ui;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.Track;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.services.config.Config;
import com.pr0gramm.app.services.config.ConfigService;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Completable;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.PublishSubject;

/**
 * Utility methods for ads.
 */
@Singleton
public class AdService {
    private final ConfigService configService;
    private final UserService userService;
    private final Settings settings;

    @Inject
    public AdService(ConfigService configService, UserService userService, Settings settings) {
        this.configService = configService;
        this.userService = userService;
        this.settings = settings;
    }

    private boolean isEnabledFor(Config.AdType type) {
        if (settings.alwaysShowAds()) {
            // If the user opted in to ads, we always show the feed ad.
            return type == Config.AdType.FEED;
        }

        // if we are on debug mode, we show ads on main.
        if (BuildConfig.DEBUG) {
            return type == Config.AdType.MAIN;
        }

        // do not show ads for premium users
        return !userService.isPremiumUser() && configService.config().adType() == type;
    }

    /**
     * Loads an ad into this view. This method also registers a listener to track the view.
     * The resulting completable completes once the ad finishes loading.
     */
    public Completable load(AdView view, Config.AdType type) {
        if (view == null) {
            return Completable.never();
        }

        // we want to have tracking and information about the ad loading.
        TrackingAdListener listener = new TrackingAdListener(type);
        view.setAdListener(listener);

        view.loadAd(new AdRequest.Builder()
                .setIsDesignedForFamilies(false)
                .addTestDevice("5436541A8134C1A32DACFD10442A32A1") // pixel
                .addTestDevice("3D53B67A1E0EA6031517DA562AF40662") // samsung
                .build());

        return listener.loadedSubject.toCompletable();
    }

    public Observable<Boolean> enabledForType(Config.AdType type) {
        return userService.loginState()
                .observeOn(AndroidSchedulers.mainThread())
                .map(info -> isEnabledFor(type))
                .startWith(isEnabledFor(type))
                .distinctUntilChanged();
    }

    private static class TrackingAdListener extends AdListener {
        private final Config.AdType adType;
        final PublishSubject<Void> loadedSubject = PublishSubject.create();

        TrackingAdListener(Config.AdType adType) {
            this.adType = adType;
        }

        @Override
        public void onAdLeftApplication() {
            Track.adClicked(adType);
        }

        @Override
        public void onAdLoaded() {
            loadedSubject.onCompleted();
        }
    }
}
