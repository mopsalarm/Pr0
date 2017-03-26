package com.pr0gramm.app.ui;

import android.content.Context;

import com.ip.sdk.Ad;
import com.ip.sdk.AdListener;
import com.ip.sdk.banner.AdView;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.Track;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.services.config.Config;
import com.pr0gramm.app.services.config.ConfigService;
import com.pr0gramm.app.util.AndroidUtility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            Track.updateAdType(config.adType());
        });
    }

    private boolean isEnabledFor(Config.AdType type) {
        if (settings.alwaysShowAds()) {
            // If the user opted in to ads, we always show the feed ad.
            return type == Config.AdType.FEED;
        }

        // do not show ads for premium users
        return !userService.isPremiumUser() && configService.config().adType() == type;
    }

    public Observable<Boolean> enabledForType(Config.AdType type) {
        return userService.loginState()
                .observeOn(AndroidSchedulers.mainThread())
                .map(info -> isEnabledFor(type))
                .startWith(isEnabledFor(type))
                .distinctUntilChanged();
    }

    public AdView newAdView(Context context, Config.AdType type) {
        AdView view = new AdView(context.getApplicationContext(), "VZV725518V7C637D");
        view.setAdspaceWidth(468);
        view.setAdspaceHeight(60);
        // view.setInternalBrowser(true);

        TrackingAdListener listener = new TrackingAdListener(type);
        view.setAdListener(listener);
        view.setTag(listener);

        int backgroundColor = AndroidUtility.resolveColorAttribute(context, android.R.attr.windowBackground);
        view.setBackgroundColor(backgroundColor);

        return view;
    }

    public Observable<AdLoadState> observeState(AdView view) {
        return ((TrackingAdListener) view.getTag()).loadedSubject;
    }

    private static class TrackingAdListener implements AdListener {
        private final Config.AdType adType;

        final Subject<AdLoadState, AdLoadState> loadedSubject = ReplaySubject
                .<AdLoadState>create().toSerialized();

        TrackingAdListener(Config.AdType adType) {
            this.adType = adType;
        }

        @Override
        public void adClicked() {
            Track.adClicked(adType);
        }

        @Override
        public void adClosed(Ad ad, boolean b) {
        }

        @Override
        public void adLoadSucceeded(Ad ad) {
            logger.info("Ad loaded successful");
            loadedSubject.onNext(AdLoadState.SUCCESS);
            loadedSubject.onCompleted();
        }

        @Override
        public void adShown(Ad ad, boolean b) {
            logger.info("Ad was shown: {}, b={}", ad, b);
        }

        @Override
        public void noAdFound() {
            logger.info("Ad could not be loaded.");
            loadedSubject.onNext(AdLoadState.FAILURE);
            loadedSubject.onCompleted();
        }
    }

    public enum AdLoadState {
        SUCCESS, FAILURE
    }

    static final Logger logger = LoggerFactory.getLogger("AdService");
}
