package com.pr0gramm.app

import android.app.Application
import android.content.Context
import android.os.StrictMode
import com.crashlytics.android.Crashlytics
import com.evernote.android.job.JobManager
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.KodeinAware
import com.github.salomonbrys.kodein.android.withContext
import com.google.android.gms.ads.MobileAds
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.sync.SyncJob
import com.pr0gramm.app.ui.ActivityErrorHandler
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.globalErrorDialogHandler
import com.pr0gramm.app.util.AndroidUtility.buildVersionCode
import com.pr0gramm.app.util.CrashlyticsLogHandler
import com.pr0gramm.app.util.LooperScheduler
import com.thefinestartist.Base
import io.fabric.sdk.android.Fabric
import net.danlew.android.joda.JodaTimeAndroid
import okhttp3.Interceptor
import org.slf4j.LoggerFactory
import pl.brightinventions.slf4android.LogLevel
import pl.brightinventions.slf4android.LoggerConfiguration
import rx.Scheduler
import rx.android.plugins.RxAndroidPlugins
import rx.android.plugins.RxAndroidSchedulersHook
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.LogManager

/**
 * Global application class for pr0gramm app.
 */
open class ApplicationClass : Application(), KodeinAware {
    private val kApp = Modules(this)

    init {
        RxAndroidPlugins.getInstance().registerSchedulersHook(object : RxAndroidSchedulersHook() {
            override fun getMainThreadScheduler(): Scheduler {
                return LooperScheduler.MAIN
            }
        })

        INSTANCE = this
    }

    override fun onCreate() {
        super.onCreate()

        Stats.init(buildVersionCode())
        JodaTimeAndroid.init(this)
        Base.initialize(this)

        Settings.initialize(this)
        Track.initialize(this)

        // do job handling & scheduling
        val jobManager = JobManager.create(this)
        jobManager.config.isVerbose = true
        jobManager.addJobCreator(SyncJob.CREATOR)

        // schedule first sync 30seconds after bootup.
        SyncJob.scheduleNextSyncIn(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            logger.info("This is a development version.")
            StrictMode.enableDefaults()

        } else {
            // allow all the dirty stuff.
            StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX)
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)

            logger.info("Initialize fabric")
            Fabric.with(this, Crashlytics())

            LoggerConfiguration.configuration()
                    .removeRootLogcatHandler()
                    .setRootLogLevel(LogLevel.INFO)
                    .addHandlerToRootLogger(CrashlyticsLogHandler())
        }

        // initialize this to show errors always in the context of the current activity.
        globalErrorDialogHandler = ActivityErrorHandler(this)

        EagerBootstrap.initEagerSingletons(withContext(this).kodein())

        // get the correct theme for the app!
        ThemeHelper.updateTheme()

        // disable verbose logging
        val log = LogManager.getLogManager().getLogger("")
        if (log != null) {
            for (h in log.handlers) {
                h.level = Level.INFO
            }
        }

        // enable ads.
        MobileAds.initialize(this, "ca-app-pub-2308657767126505~4138045673")
        MobileAds.setAppVolume(0f)
        MobileAds.setAppMuted(true)
    }

    /**
     * Overridden in debug application to provide the stetho interceptor.
     */
    open fun debugNetworkInterceptor(): Interceptor {
        return Interceptor { it.proceed(it.request()) }
    }

    override val kodein: Kodein
        get() = kApp.kodein

    companion object {
        private val logger = LoggerFactory.getLogger("Pr0grammApplication")

        private lateinit var INSTANCE: ApplicationClass

        @JvmStatic
        fun get(context: Context): ApplicationClass {
            return context.applicationContext as ApplicationClass
        }
    }
}
