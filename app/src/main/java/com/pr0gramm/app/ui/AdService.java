package com.pr0gramm.app.ui;

import android.content.Context;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.Track;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.services.config.Config;
import com.pr0gramm.app.services.config.ConfigService;
import com.pr0gramm.app.util.AndroidUtility;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

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

        // track that we use ads.
        configService.observeConfig().subscribe(config -> {
            Track.updateAdType(config.getAdType());
        });
    }

    private boolean isEnabledFor(Config.AdType type) {
        if (settings.getAlwaysShowAds()) {
            // If the user opted in to ads, we always show the feed ad.
            return type == Config.AdType.FEED;
        }

        // do not show ads for premium users
        return !userService.isPremiumUser() && configService.config().getAdType() == type;
    }

    /**
     * Loads an ad into this view. This method also registers a listener to track the view.
     * The resulting completable completes once the ad finishes loading.
     */
    public Observable<AdLoadState> load(AdView view, Config.AdType type) {
        if (view == null) {
            return Observable.empty();
        }

        // we want to have tracking and information about the ad loading.
        TrackingAdListener listener = new TrackingAdListener(type);
        view.setAdListener(listener);

        view.loadAd(new AdRequest.Builder()
                .setIsDesignedForFamilies(false)
                // .addTestDevice("5436541A8134C1A32DACFD10442A32A1") // pixel
                .build());

        return listener.loadedSubject;
    }

    public Observable<Boolean> enabledForType(Config.AdType type) {
        return userService.loginState()
                .observeOn(AndroidSchedulers.mainThread())
                .map(info -> isEnabledFor(type))
                .startWith(isEnabledFor(type))
                .distinctUntilChanged();
    }

    public AdView newAdView(Context context) {
        AdView view = new AdView(context.getApplicationContext());
        view.setAdUnitId(context.getString(R.string.banner_ad_unit_id));

        int backgroundColor = AndroidUtility.resolveColorAttribute(context, android.R.attr.windowBackground);
        view.setBackgroundColor(backgroundColor);

        return view;
    }

    private static class TrackingAdListener extends AdListener {
        private final Config.AdType adType;

        final Subject<AdLoadState, AdLoadState> loadedSubject = ReplaySubject
                .<AdLoadState>create().toSerialized();

        TrackingAdListener(Config.AdType adType) {
            this.adType = adType;
        }

        @Override
        public void onAdLeftApplication() {
            Track.adClicked(adType);
        }

        @Override
        public void onAdLoaded() {
            loadedSubject.onNext(AdLoadState.SUCCESS);
            loadedSubject.onCompleted();
        }

        @Override
        public void onAdFailedToLoad(int i) {
            loadedSubject.onNext(AdLoadState.FAILURE);
            loadedSubject.onCompleted();
        }
    }

    public enum AdLoadState {
        SUCCESS, FAILURE
    }
}
