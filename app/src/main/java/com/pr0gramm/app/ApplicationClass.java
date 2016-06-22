package com.pr0gramm.app;

import android.app.Application;
import android.content.Context;
import android.os.StrictMode;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.f2prateek.dart.Dart;
import com.orm.SugarContext;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.ui.ActivityErrorHandler;
import com.pr0gramm.app.util.CrashlyticsLogHandler;
import com.pr0gramm.app.util.Lazy;
import com.pr0gramm.app.util.LooperScheduler;
import com.thefinestartist.Base;

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
import static com.pr0gramm.app.util.AndroidUtility.buildVersionCode;

/**
 * Global application class for pr0gramm app.
 */
public class ApplicationClass extends Application {
    private static final Logger logger = LoggerFactory.getLogger("Pr0grammApplication");

    final Lazy<AppComponent> appComponent = Lazy.of(() -> DaggerAppComponent.builder()
            .appModule(new AppModule(this))
            .httpModule(new HttpModule())
            .build());

    @Override
    public void onCreate() {
        super.onCreate();

        Stats.init(buildVersionCode());
        SugarContext.init(this);
        JodaTimeAndroid.init(this);
        Base.initialize(this);

        Settings.initialize(this);

        if (BuildConfig.DEBUG) {
            logger.info("This is a development version.");
            StrictMode.enableDefaults();
            ButterKnife.setDebug(true);
            Dart.setDebug(true);

        } else {
            logger.info("Initialize Fabric");
            Fabric.with(this, new Crashlytics());
            Fabric.with(this, new Answers());

            LoggerConfiguration.configuration()
                    .removeRootLogcatHandler()
                    .addHandlerToRootLogger(new CrashlyticsLogHandler());
        }

        // initialize this to show errors always in the context of the current activity.
        setGlobalErrorDialogHandler(new ActivityErrorHandler(this));

        Dagger.initEagerSingletons(this);

        if (BuildConfig.DEBUG) {
            StethoWrapper.init(this);
        }

        // get the correct theme for the app!
        ThemeHelper.updateTheme(this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        SugarContext.terminate();
    }


    public static ApplicationClass get(Context context) {
        return (ApplicationClass) context.getApplicationContext();
    }

    static {
        RxAndroidPlugins.getInstance().registerSchedulersHook(new RxAndroidSchedulersHook() {
            @Override
            public Scheduler getMainThreadScheduler() {
                return LooperScheduler.MAIN;
            }
        });
    }
}
