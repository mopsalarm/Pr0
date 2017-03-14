package com.pr0gramm.app.services;

import android.app.Application;

import com.google.firebase.analytics.FirebaseAnalytics;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 */
@Module
public class TrackingModule {
    @Provides
    @Singleton
    public FirebaseAnalytics firebaseAnalyticsTracker(Application app) {
        return FirebaseAnalytics.getInstance(app);
    }
}
