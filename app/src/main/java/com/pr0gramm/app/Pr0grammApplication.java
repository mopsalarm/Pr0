package com.pr0gramm.app;

import android.app.Application;

import net.danlew.android.joda.JodaTimeAndroid;

import roboguice.RoboGuice;

/**
 * Global application class for pr0gramm app.
 */
public class Pr0grammApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        JodaTimeAndroid.init(this);
    }

    static {
        RoboGuice.setUseAnnotationDatabases(false);
    }
}
