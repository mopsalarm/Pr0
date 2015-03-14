package com.pr0gramm.app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.common.base.Throwables;
import com.orm.SugarApp;
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment;

import net.danlew.android.joda.JodaTimeAndroid;

import io.fabric.sdk.android.Fabric;
import roboguice.RoboGuice;
import rx.plugins.RxJavaErrorHandler;
import rx.plugins.RxJavaPlugins;

/**
 * Global application class for pr0gramm app.
 */
public class Pr0grammApplication extends SugarApp {
    public Pr0grammApplication() {

        // handle exceptions!
        RxJavaPlugins.getInstance().registerErrorHandler(new RxJavaErrorHandler() {
            @Override
            public void handleError(Throwable error) {
                try {
                    // handle the error.
                    ErrorDialogFragment.handle(Pr0grammApplication.this, error);
                } catch (Throwable err) {
                    err.printStackTrace();
                }
            }
        });
    }

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
