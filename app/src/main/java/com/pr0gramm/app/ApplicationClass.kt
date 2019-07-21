package com.pr0gramm.app

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import com.google.android.gms.ads.MobileAds
import com.llamalab.safs.FileSystems
import com.llamalab.safs.android.AndroidFileSystem
import com.llamalab.safs.android.AndroidFileSystemProvider
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.sync.SyncStatsWorker
import com.pr0gramm.app.sync.SyncWorker
import com.pr0gramm.app.ui.ActivityErrorHandler
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.globalErrorDialogHandler
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.AndroidUtility.buildVersionCode
import com.pr0gramm.app.util.di.InjectorAware
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory
import io.sentry.context.Context
import io.sentry.context.ContextManager
import io.sentry.event.Breadcrumb
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

    private val logger = Logger("Pr0grammApp")

    companion object {
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

            System.setProperty(
                    "com.llamalab.safs.spi.DefaultFileSystemProvider",
                    AndroidFileSystemProvider::class.java.name)
        }
    }

    override fun onCreate() {
        super.onCreate()

        setupSentry()

        // handler to ignore certain exceptions before they reach sentry
        ExceptionHandler.install(this)

        val fs = FileSystems.getDefault() as AndroidFileSystem
        fs.context = this

        Stats.init(buildVersionCode())

        Settings.initialize(this)
        Track.initialize(this)

        WorkManager.initialize(this, Configuration.Builder()
                .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.VERBOSE else Log.INFO)
                .build())

        forceInjectorInstance()

        doInBackground {
            // schedule first sync 30seconds after bootup.
            SyncWorker.scheduleNextSyncIn(this@ApplicationClass, 30, TimeUnit.SECONDS)

            // also schedule the nightly update job
            SyncStatsWorker.schedule(this@ApplicationClass)
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

    private fun setupSentry() {
        // setup sentry
        val sentryToken = "https://a16a17b965a44a9eb100bc0af7f4d684@sentry.io/1507302"

        val sentry = Sentry.init(sentryToken, CustomSentryClientFactory(this))
        sentry.environment = if (BuildConfig.DEBUG) "debug" else "prod"
        sentry.release = BuildConfig.VERSION_NAME


        val levels = arrayOf(null, null, Breadcrumb.Level.DEBUG, Breadcrumb.Level.DEBUG,
                Breadcrumb.Level.INFO, Breadcrumb.Level.WARNING, Breadcrumb.Level.ERROR,
                Breadcrumb.Level.CRITICAL)

        // add log messages to sentry breadcrumbs
        Logging.remoteLoggingHandler = { level, tag, message ->
            if (level >= Log.INFO) {
                recordBreadcrumb {
                    setCategory("log")
                    setLevel(levels.getOrNull(level) ?: Breadcrumb.Level.INFO)
                    setMessage("$tag: $message")
                }
            }
        }
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

            try {
                MobileAds.initialize(this@ApplicationClass, id)
            } catch (ignored: NullPointerException) {
                // for some reason an internal getVersionString returns null,
                // and the resulti s not checked. We ignore the error in that case
            }

            MobileAds.setAppVolume(0f)
            MobileAds.setAppMuted(true)
        }
    }

    override val injector by lazy { appInjector(this) }
}


private class CustomSentryClientFactory(ctx: android.content.Context) : AndroidSentryClientFactory(ctx)

private class SentryContextManager : ContextManager {
    private val context = Context(256)

    override fun clear() {
        context.clear()
    }

    override fun getContext(): Context {
        return context
    }
}