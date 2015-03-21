package com.pr0gramm.app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.common.base.Throwables;
import com.orm.SugarApp;

import net.danlew.android.joda.JodaTimeAndroid;

import io.fabric.sdk.android.Fabric;
import roboguice.RoboGuice;

/**
 * Global application class for pr0gramm app.
 */
public class Pr0grammApplication extends SugarApp {
    @Override
    public void onCreate() {
        super.onCreate();

        JodaTimeAndroid.init(this);

        boolean development = getPackageInfo(this).versionName.endsWith(".dev");
        if (!development) {
            Settings settings = Settings.of(this);
            if (settings.crashlyticsEnabled())
                Fabric.with(this, new Crashlytics());
        } else {
            Log.i("App", "This is a development version.");
        }
    }

    public static PackageInfo getPackageInfo(Context context) {
        PackageManager packageManager = context.getPackageManager();
        try {
            return packageManager.getPackageInfo(context.getPackageName(), 0);

        } catch (PackageManager.NameNotFoundException err) {
            throw Throwables.propagate(err);
        }
    }

    static {
        RoboGuice.setUseAnnotationDatabases(false);
    }
}
