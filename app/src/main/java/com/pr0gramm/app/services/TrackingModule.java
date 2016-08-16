package com.pr0gramm.app.services;

import android.app.Application;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.services.config.ConfigService;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 */
@Module
public class TrackingModule {
    private static final String PROPERTY_ID = "UA-61398904-3";

    @Provides
    @Singleton
    public Tracker googleAnalyticsTracker(Application app, ConfigService configService) {
        GoogleAnalytics analytics = GoogleAnalytics.getInstance(app);
        Tracker tracker = analytics.newTracker(PROPERTY_ID);
        tracker.enableAdvertisingIdCollection(true);
        tracker.enableAutoActivityTracking(true);
        tracker.enableExceptionReporting(true);
        tracker.setAppVersion(String.valueOf(BuildConfig.VERSION_NAME));

        tracker.setSampleRate(25);

        configService.observeConfig().subscribe(config -> {
            tracker.setSampleRate(config.googleAnalyticsSampleRate());
        });

        return tracker;
    }
}
