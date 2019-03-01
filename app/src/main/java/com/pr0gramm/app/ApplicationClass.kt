package com.pr0gramm.app

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import com.crashlytics.android.Crashlytics
import com.google.android.gms.ads.MobileAds
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.sync.SyncStatsWorker
import com.pr0gramm.app.sync.SyncWorker
import com.pr0gramm.app.ui.ActivityErrorHandler
import com.pr0gramm.app.ui.AdMobWorkaround
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.globalErrorDialogHandler
import com.pr0gramm.app.util.AndroidUtility.buildVersionCode
import com.pr0gramm.app.util.CachedThreadScheduler
import com.pr0gramm.app.util.ExceptionHandler
import com.pr0gramm.app.util.debug
import com.pr0gramm.app.util.di.InjectorAware
import com.pr0gramm.app.util.doInBackground
import io.fabric.sdk.android.Fabric
import io.fabric.sdk.android.SilentLogger
import rx.Scheduler
import rx.plugins.RxJavaPlugins
import rx.plugins.RxJavaSchedulersHook
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.LogManager

/**
 * Global application class for pr0gramm app.
 */
open class ApplicationClass : Application(), InjectorAware {
    private val bootupWatch = Stopwatch()

    private val logger = Logger("Pr0grammApplication")

    init {
        if (BuildConfig.DEBUG) {
            StrictMode.enableDefaults()

        } else {
            // allow all the dirty stuff.
            StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX)
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
        }

        RxJavaPlugins.getInstance().registerSchedulersHook(object : RxJavaSchedulersHook() {
            private val betterIoScheduler = CachedThreadScheduler("RxIoScheduler")

            override fun getIOScheduler(): Scheduler {
                return betterIoScheduler
            }
        })
    }

    override fun onCreate() {
        super.onCreate()

        if (!BuildConfig.DEBUG) {
            Log.i("pr0gramm", "Initialize fabric/crashlytics in Application::onCreate")
            Fabric.with(Fabric.Builder(this)
                    .debuggable(false)
                    .logger(SilentLogger())
                    .kits(Crashlytics())
                    .build())

            // setup log forwarding in production
            Logging.remoteLoggingHandler = Crashlytics.getInstance().core::log
        }

        // handler to ignore certain exceptions before they reach crashlytics.
        ExceptionHandler.install(this)

        Stats.init(buildVersionCode())

        Settings.initialize(this)
        Track.initialize(this)

        AdMobWorkaround.install(this)

        WorkManager.initialize(this, Configuration.Builder()
                .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.VERBOSE else Log.INFO)
                .build())

        forceInjectorInstance()

        doInBackground {
            // schedule first sync 30seconds after bootup.
            SyncWorker.scheduleNextSyncIn(30, TimeUnit.SECONDS)

            // also schedule the nightly update job
            SyncStatsWorker.schedule()
        }

        // initialize this to show errors always in the context of the current activity.
        globalErrorDialogHandler = ActivityErrorHandler(this)

        // get the correct theme for the app!
        ThemeHelper.updateTheme()

        if (!BuildConfig.DEBUG) {
            // disable verbose logging
            val log = LogManager.getLogManager().getLogger("")
            log?.handlers?.forEach { it.level = Level.INFO }
        }

        // initialize mobile ads asynchronously
        initializeMobileAds()

        logger.info { "App booted in $bootupWatch" }

        Stats().histogram("app.boot.time", bootupWatch.elapsed().millis)
    }

    private fun forceInjectorInstance() {
        // ensure that the lazy creates the instance
        System.identityHashCode(injector)

        debug {
            // validate that all dependencies can be created.
            injector.validate()
        }
    }

    private fun initializeMobileAds() {
        doInBackground {
            val id = if (BuildConfig.DEBUG) {
                "ca-app-pub-3940256099942544~3347511713"
            } else {
                "ca-app-pub-2308657767126505~4138045673"
            }

            MobileAds.initialize(this@ApplicationClass, id)
            MobileAds.setAppVolume(0f)
            MobileAds.setAppMuted(true)
        }
    }

    override val injector by lazy { appInjector(this) }
}

