package com.pr0gramm.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.StrictMode;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.f2prateek.dart.Dart;
import com.google.common.base.Throwables;
import com.orm.SugarApp;
import com.pr0gramm.app.ui.ActivityErrorHandler;
import com.pr0gramm.app.util.CrashlyticsLogHandler;
import com.pr0gramm.app.util.HandlerThreadScheduler;
import com.pr0gramm.app.util.Lazy;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

import net.danlew.android.joda.JodaTimeAndroid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import butterknife.ButterKnife;
import io.fabric.sdk.android.Fabric;
import pl.brightinventions.slf4android.LoggerConfiguration;
import rx.Scheduler;
import rx.android.plugins.RxAndroidPlugins;
import rx.android.plugins.RxAndroidSchedulersHook;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.setGlobalErrorDialogHandler;

/**
 * Global application class for pr0gramm app.
 */
public class Pr0grammApplication extends SugarApp {
    private static final Logger logger = LoggerFactory.getLogger(Pr0grammApplication.class);

    private RefWatcher refWatcher;

    final Lazy<AppComponent> appComponent = Lazy.of(() -> DaggerAppComponent.builder()
            .appModule(new AppModule(this))
            .httpModule(new HttpModule())
            .build());

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
                Fabric.with(this, new Answers());

                LoggerConfiguration.configuration()
                        .removeRootLogcatHandler()
                        .addHandlerToRootLogger(new CrashlyticsLogHandler());
            } else {
                // just disable logging
                LoggerConfiguration.configuration()
                        .removeRootLogcatHandler();
            }
        } else {
            logger.info("This is a development version.");
            StrictMode.enableDefaults();
            ButterKnife.setDebug(true);
            Dart.setDebug(true);
        }

        // initialize this to show errors always in the context of the current activity.
        setGlobalErrorDialogHandler(new ActivityErrorHandler(this));

        RxAndroidPlugins.getInstance().registerSchedulersHook(new RxAndroidSchedulersHook() {
            @Override
            public Scheduler getMainThreadScheduler() {
                return HandlerThreadScheduler.INSTANCE;
            }
        });
    }

    public static PackageInfo getPackageInfo() {
        PackageManager packageManager = GLOBAL_CONTEXT.getPackageManager();
        try {
            return packageManager.getPackageInfo(GLOBAL_CONTEXT.getPackageName(), 0);

        } catch (PackageManager.NameNotFoundException err) {
            throw Throwables.propagate(err);
        }
    }

    public static Context GLOBAL_CONTEXT;

    /**
     * Opens the community in the playstore.
     */
    public static void openCommunityWebpage(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("http://example.com/"));

        activity.startActivity(intent);
    }

    /**
     * Returns the global ref watcher instance
     */
    public static RefWatcher getRefWatcher() {
        return ((Pr0grammApplication) GLOBAL_CONTEXT).refWatcher;
    }

    public static Pr0grammApplication get(Context context) {
        return (Pr0grammApplication) context.getApplicationContext();
    }
}
