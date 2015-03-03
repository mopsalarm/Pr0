package com.pr0gramm.app;

import android.app.Application;

import com.crashlytics.android.Crashlytics;

import net.danlew.android.joda.JodaTimeAndroid;

import io.fabric.sdk.android.Fabric;
import roboguice.RoboGuice;

/**
 * Global application class for pr0gramm app.
 */
public class Pr0grammApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        JodaTimeAndroid.init(this);

        Settings settings = Settings.of(this);
        if (settings.crashlyticsEnabled())
            Fabric.with(this, new Crashlytics());
    }

    static {
        RoboGuice.setUseAnnotationDatabases(false);
    }
}
