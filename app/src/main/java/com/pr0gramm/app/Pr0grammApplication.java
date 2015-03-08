package com.pr0gramm.app;

import com.crashlytics.android.Crashlytics;
import com.orm.SugarApp;

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

        Settings settings = Settings.of(this);
        if (settings.crashlyticsEnabled())
            Fabric.with(this, new Crashlytics());
    }

    static {
        RoboGuice.setUseAnnotationDatabases(false);
    }
}
