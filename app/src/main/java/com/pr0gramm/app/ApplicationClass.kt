package com.pr0gramm.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.internal.common.CrashlyticsCore
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.sync.SyncStatsWorker
import com.pr0gramm.app.sync.SyncWorker
import com.pr0gramm.app.ui.ActivityErrorHandler
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.GlobalErrorDialogHandler
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.AndroidUtility.buildVersionCode
import com.pr0gramm.app.util.ExceptionHandler
import com.pr0gramm.app.util.debugOnly
import com.pr0gramm.app.util.di.InjectorAware
import com.pr0gramm.app.util.doInBackground
import kotlinx.coroutines.runBlocking
import java.util.WeakHashMap
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
        debugOnly {
            System.setProperty("kotlinx.coroutines.debug", "on")
        }

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
        }

        lateinit var appContext: Context
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

        // wait for firebase setup to finish
        runBlocking {
            firebaseJob.join()
        }

        logger.info { "App booted in $bootupWatch" }

        Stats().histogram("app.boot.time", bootupWatch.elapsed().inMillis)
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
                    val levelStr = Logging.levels.getOrNull(level)
                    core.log("$levelStr [$tag]: $message")
                }
            }
        }
    }

    private val lifecycleCallbacksLookup = WeakHashMap<ActivityLifecycleCallbacks, CatchingActivityLifecycleCallbacks>()

    override fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        // Issue with crashlytics / measurement. onActivityCreate throws a 'ConcurrentModificationException'
        // that then crashes the instance. This way we can catch & ignore issues directly.
        val wrapped = CatchingActivityLifecycleCallbacks(callback)
        lifecycleCallbacksLookup[callback] = wrapped
        super.registerActivityLifecycleCallbacks(wrapped)
    }

    override fun unregisterActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        super.unregisterActivityLifecycleCallbacks(lifecycleCallbacksLookup[callback] ?: callback)
    }

    private fun forceInjectorInstance() {
        // ensure that the lazy creates the instance
        System.identityHashCode(injector)

        debugOnly {
            // validate that all dependencies can be created.
            injector.validate()
        }
    }

    override val injector by lazy { appInjector(this) }
}


private class CatchingActivityLifecycleCallbacks(
        private val callback: Application.ActivityLifecycleCallbacks)
    : Application.ActivityLifecycleCallbacks by callback {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        try {
            callback.onActivityCreated(activity, savedInstanceState)
        } catch (err: RuntimeException) {
            AndroidUtility.logToCrashlytics(IllegalStateException(
                    "ActivityLifecycleCallback failed with ${err.javaClass.simpleName}",
                    err))
        }
    }
}
