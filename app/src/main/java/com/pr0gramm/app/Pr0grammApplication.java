package com.pr0gramm.app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;
import com.google.common.base.Throwables;
import com.orm.SugarApp;

import net.danlew.android.joda.JodaTimeAndroid;

import io.fabric.sdk.android.Fabric;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Global application class for pr0gramm app.
 */
public class Pr0grammApplication extends SugarApp {
    public Pr0grammApplication() {
        GLOBAL_CONTEXT = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        JodaTimeAndroid.init(this);

        GoogleAnalytics ga = GoogleAnalytics.getInstance(this);

        boolean development = getPackageInfo(this).versionName.endsWith(".dev");
        if (!development) {
            Settings settings = Settings.of(this);
            if (settings.analyticsEnabled()) {
                ga.setAppOptOut(!settings.analyticsEnabled());
                Fabric.with(this, new Crashlytics());
            }
        } else {
            Log.i("App", "This is a development version.");
            // ga.setDryRun(true);
        }

        // initialize the tracker once the app was created
        tracker = ga.newTracker("UA-61398904-1");
        tracker.setAnonymizeIp(true);
        tracker.enableAutoActivityTracking(true);

        // we use crashlytics for that.
        tracker.enableExceptionReporting(false);
    }

    public static PackageInfo getPackageInfo(Context context) {
        PackageManager packageManager = context.getPackageManager();
        try {
            return packageManager.getPackageInfo(context.getPackageName(), 0);

        } catch (PackageManager.NameNotFoundException err) {
            throw Throwables.propagate(err);
        }
    }

    public static Context GLOBAL_CONTEXT;

    private static Tracker tracker;

    public static synchronized Tracker tracker() {
        return checkNotNull(tracker, "Application is not yet initialized!");
    }
}
