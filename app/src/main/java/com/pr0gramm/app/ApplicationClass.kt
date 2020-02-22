package com.pr0gramm.app

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.core.CrashlyticsCore
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.sync.SyncStatsWorker
import com.pr0gramm.app.sync.SyncWorker
import com.pr0gramm.app.ui.ActivityErrorHandler
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.GlobalErrorDialogHandler
import com.pr0gramm.app.util.AndroidUtility.buildVersionCode
import com.pr0gramm.app.util.CachedThreadScheduler
import com.pr0gramm.app.util.ExceptionHandler
import com.pr0gramm.app.util.debugOnly
import com.pr0gramm.app.util.di.InjectorAware
import com.pr0gramm.app.util.doInBackground
import kotlinx.coroutines.runBlocking
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

    init {
        appContext = this
    }

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
        }

        lateinit var appContext: android.content.Context
    }

    override fun onCreate() = logger.time("onCreate") {
        super.onCreate()

        val firebaseJob = doInBackground {
            logger.time("Initialize firebase") {
                initializeFirebase()

                // handler to ignore certain exceptions before they reach firebase
                ExceptionHandler.install(this)
            }
        }

        Stats.init(buildVersionCode())

        Settings.initialize(this)
        Track.initialize(this)

        logger.time("Initializing WorkManager") {
            WorkManager.initialize(this, Configuration.Builder()
                    .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.VERBOSE else Log.INFO)
                    .build())
        }

        forceInjectorInstance()

        doInBackground {
            // schedule first sync 30seconds after bootup.
            SyncWorker.scheduleNextSyncIn(this@ApplicationClass, 30, TimeUnit.SECONDS, sourceTag = "Bootup")

            // also schedule the nightly update job
            SyncStatsWorker.schedule(this@ApplicationClass)
        }

        // initialize this to show errors always in the context of the current activity.
        GlobalErrorDialogHandler = ActivityErrorHandler(this)

        // get the correct theme for the app!
        ThemeHelper.updateTheme()

        if (!BuildConfig.DEBUG) {
            // disable verbose logging
            val log = LogManager.getLogManager().getLogger("")
            log?.handlers?.forEach { it.level = Level.INFO }
        }

        // initialize mobile ads asynchronously
        initializeMobileAds()

        // wait for firebase setup to finish
        runBlocking {
            firebaseJob.join()
        }

        logger.info { "App booted in $bootupWatch" }

        Stats().histogram("app.boot.time", bootupWatch.elapsed().millis)
    }

    private fun initializeFirebase() {
        FirebaseApp.initializeApp(this)

        val fc = FirebaseCrashlytics.getInstance()

        val core = try {
            val field = FirebaseCrashlytics::class.java.declaredFields.first {
                it.type === CrashlyticsCore::class.java
            }

            field.isAccessible = true
            field.get(fc) as CrashlyticsCore

        } catch (err: Throwable) {
            debugOnly { throw err }
            null
        }

        if (core != null) {
            Logging.configureLoggingOutput { level, tag, message ->
                if (level >= Log.INFO) {
                    core.log(level, tag, message)

                    true
                } else {
                    false
                }
            }
        }
    }

    private fun forceInjectorInstance() {
        // ensure that the lazy creates the instance
        System.identityHashCode(injector)

        debugOnly {
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

