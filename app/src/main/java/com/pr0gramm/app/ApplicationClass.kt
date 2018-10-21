package com.pr0gramm.app

import android.app.Application
import android.os.StrictMode
import android.util.Log
import com.crashlytics.android.Crashlytics
import com.evernote.android.job.JobConfig
import com.evernote.android.job.JobManager
import com.google.android.gms.ads.MobileAds
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.sync.SyncJob
import com.pr0gramm.app.sync.SyncStatisticsJob
import com.pr0gramm.app.ui.ActivityErrorHandler
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.globalErrorDialogHandler
import com.pr0gramm.app.util.AndroidUtility.buildVersionCode
import com.pr0gramm.app.util.ExceptionHandler
import com.pr0gramm.app.util.SimpleJobLogger
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.logger
import io.fabric.sdk.android.Fabric
import io.fabric.sdk.android.SilentLogger
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.direct
import org.kodein.di.erased.bind
import org.kodein.di.erased.instance
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.LogManager

/**
 * Global application class for pr0gramm app.
 */
open class ApplicationClass : Application(), KodeinAware {
    private val startup = System.currentTimeMillis()

    private val logger = logger("Pr0grammApplication")

    init {
        if (BuildConfig.DEBUG) {
            StrictMode.enableDefaults()

        } else {
            // allow all the dirty stuff.
            StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX)
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
        }
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
        }

        // handler to ignore certain exceptions before they reach crashlytics.
        ExceptionHandler.install()

        Stats.init(buildVersionCode())

        Settings.initialize(this)
        Track.initialize(this)

        JobConfig.setLogcatEnabled(BuildConfig.DEBUG)
        JobConfig.addLogger(SimpleJobLogger())

        val eagerSingletons = EagerBootstrap.initEagerSingletons { kodein.direct }

        // do job handling & scheduling
        val jobManager = JobManager.create(this)
        jobManager.addJobCreator(SyncJob.CREATOR)
        jobManager.addJobCreator(SyncStatisticsJob.CREATOR)

        // schedule first sync 30seconds after bootup.
        SyncJob.scheduleNextSyncIn(30, TimeUnit.SECONDS)

        // also schedule the nightly update job
        SyncStatisticsJob.schedule()

        // initialize this to show errors always in the context of the current activity.
        globalErrorDialogHandler = ActivityErrorHandler(this)

        // get the correct theme for the app!
        ThemeHelper.updateTheme()

        if (!BuildConfig.DEBUG) {
            // disable verbose logging
            val log = LogManager.getLogManager().getLogger("")
            log?.handlers?.forEach { it.level = Level.INFO }
        }

        doInBackground {
            if (BuildConfig.DEBUG) {
                // test ads for debug, see https://developers.google.com/admob/android/test-ads
                MobileAds.initialize(this, "ca-app-pub-3940256099942544~3347511713")
            } else {
                MobileAds.initialize(this, "ca-app-pub-2308657767126505~4138045673")
            }

            MobileAds.setAppVolume(0f)
            MobileAds.setAppMuted(true)
        }

        eagerSingletons.await()

        val bootupTime = System.currentTimeMillis() - startup
        logger.info { "App booted in ${bootupTime}ms" }

        Stats.get().incrementCounter("app.booted")
        Stats.get().histogram("app.boot.time", bootupTime)
    }

    override val kodein: Kodein = Kodein.lazy { configureKodein(this) }

    protected open fun configureKodein(builder: Kodein.MainBuilder) {
        builder.apply {
            val app = this@ApplicationClass
            bind<Application>() with instance(app)

            import(appModule(app))
            import(httpModule(app))
            import(trackingModule(app))
            import(servicesModule(app))
        }
    }
}

