package com.pr0gramm.app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.crashlytics.android.Crashlytics;
import com.google.common.base.Throwables;
import com.orm.SugarApp;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

import net.danlew.android.joda.JodaTimeAndroid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric.sdk.android.Fabric;
import pl.brightinventions.slf4android.LoggerConfiguration;

/**
 * Global application class for pr0gramm app.
 */
public class Pr0grammApplication extends SugarApp {
    private static final Logger logger = LoggerFactory.getLogger(Pr0grammApplication.class);
    private RefWatcher refWatcher;

    public Pr0grammApplication() {
        GLOBAL_CONTEXT = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        refWatcher = LeakCanary.install(this);
        JodaTimeAndroid.init(this);

        boolean development = getPackageInfo().versionName.endsWith(".dev");
        if (!development) {
            Settings settings = Settings.of(this);
            if (settings.analyticsEnabled()) {
                logger.info("Initialize Fabric");
                Fabric.with(this, new Crashlytics());

                LoggerConfiguration.configuration()
                        .removeRootLogcatHandler()
                        .addHandlerToRootLogger(new CrashlyticsLogHandler());
            }
        } else {
            logger.info("This is a development version.");
        }
    }

    public static PackageInfo getPackageInfo() {
        PackageManager packageManager = GLOBAL_CONTEXT.getPackageManager();
        try {
            return packageManager.getPackageInfo(GLOBAL_CONTEXT.getPackageName(), 0);

        } catch (PackageManager.NameNotFoundException err) {
            throw Throwables.propagate(err);
        }
    }

    public static RefWatcher getRefWatcher() {
        Pr0grammApplication application = (Pr0grammApplication) GLOBAL_CONTEXT.getApplicationContext();
        return application.refWatcher;
    }

    public static Context GLOBAL_CONTEXT;
}
