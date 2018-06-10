package com.pr0gramm.app

import android.app.Application
import android.os.StrictMode
import com.crashlytics.android.Crashlytics
import com.evernote.android.job.JobConfig
import com.evernote.android.job.JobManager
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.KodeinAware
import com.github.salomonbrys.kodein.android.autoAndroidModule
import com.github.salomonbrys.kodein.android.withContext
import com.github.salomonbrys.kodein.lazy
import com.google.android.gms.ads.MobileAds
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.sync.SyncJob
import com.pr0gramm.app.sync.SyncStatisticsJob
import com.pr0gramm.app.ui.ActivityErrorHandler
import com.pr0gramm.app.ui.TagInputView
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.globalErrorDialogHandler
import com.pr0gramm.app.util.AndroidUtility.buildVersionCode
import com.pr0gramm.app.util.LogHandler
import com.pr0gramm.app.util.SimpleJobLogger
import com.pr0gramm.app.util.ignoreException
import io.fabric.sdk.android.Fabric
import net.danlew.android.joda.JodaTimeAndroid
import okhttp3.Interceptor
import org.slf4j.LoggerFactory
import pl.brightinventions.slf4android.LogLevel
import pl.brightinventions.slf4android.LoggerConfiguration
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.LogManager

/**
 * Global application class for pr0gramm app.
 */
open class ApplicationClass : Application(), KodeinAware {
    private val logger = LoggerFactory.getLogger("Pr0grammApplication")

    override fun onCreate() {
        super.onCreate()

        Stats.init(buildVersionCode())
        JodaTimeAndroid.init(this)

        Settings.initialize(this)
        Track.initialize(this)
        TagInputView.initialize(this)

        JobConfig.setLogcatEnabled(BuildConfig.DEBUG)
        JobConfig.addLogger(SimpleJobLogger())

        // do job handling & scheduling
        val jobManager = JobManager.create(this)
        jobManager.addJobCreator(SyncJob.CREATOR)
        jobManager.addJobCreator(SyncStatisticsJob.CREATOR)

        // schedule first sync 30seconds after bootup.
        SyncJob.scheduleNextSyncIn(30, TimeUnit.SECONDS)

        // also schedule the nightly update job
        SyncStatisticsJob.schedule()

        if (BuildConfig.DEBUG) {
            logger.info("This is a development version.")
            StrictMode.enableDefaults()

        } else {
            // allow all the dirty stuff.
            StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX)
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)

            logger.info("Initialize fabric")
            Fabric.with(this, Crashlytics())
        }

        LoggerConfiguration.configuration()
                .removeRootLogcatHandler()
                .setRootLogLevel(LogLevel.INFO)
                .addHandlerToRootLogger(LogHandler())

        // initialize this to show errors always in the context of the current activity.
        globalErrorDialogHandler = ActivityErrorHandler(this)

        EagerBootstrap.initEagerSingletons(withContext(this).kodein())

        // get the correct theme for the app!
        ThemeHelper.updateTheme()

        if (!BuildConfig.DEBUG) {
            // disable verbose logging
            val log = LogManager.getLogManager().getLogger("")
            for (h in log?.handlers ?: arrayOf()) {
                h.level = Level.INFO
            }
        }

        // enable ads - do not fail if we get an exception from this shitty google library.
        ignoreException {
            if (BuildConfig.DEBUG) {
                // test ads for debug, see https://developers.google.com/admob/android/test-ads
                MobileAds.initialize(this, "ca-app-pub-3940256099942544/6300978111")
            } else {
                MobileAds.initialize(this, "ca-app-pub-2308657767126505~4138045673")
            }

            MobileAds.setAppVolume(0f)
            MobileAds.setAppMuted(true)
        }

        Stats.get().incrementCounter("app.booted")
    }

    /**
     * Overridden in debug application to provide the stetho interceptor.
     */
    open fun debugNetworkInterceptor(): Interceptor {
        return Interceptor { it.proceed(it.request()) }
    }

    override val kodein: Kodein by Kodein.lazy {
        val app = this@ApplicationClass
        import(autoAndroidModule(app))
        import(appModule(app), allowOverride = true)
        import(httpModule(app))
        import(trackingModule(app))
        import(servicesModule(app))
    }
}

