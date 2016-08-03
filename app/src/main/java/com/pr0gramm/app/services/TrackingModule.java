package com.pr0gramm.app.services;

import android.app.Application;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;
import com.pr0gramm.app.BuildConfig;

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
    public Tracker googleAnalyticsTracker(Application app) {
        GoogleAnalytics analytics = GoogleAnalytics.getInstance(app);
        Tracker tracker = analytics.newTracker(PROPERTY_ID);
        tracker.enableAdvertisingIdCollection(true);
        tracker.enableAutoActivityTracking(true);
        tracker.enableExceptionReporting(true);
        tracker.setAppVersion(String.valueOf(BuildConfig.VERSION_NAME));

        tracker.setSampleRate(0.01);

        return tracker;
    }
}
