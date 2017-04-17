package com.pr0gramm.app

import android.app.Application
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.google.android.gms.analytics.GoogleAnalytics
import com.google.android.gms.analytics.Tracker

/**
 */
fun trackingModule(app: Application) = Kodein.Module {
    val PROPERTY_ID = "UA-61398904-3"

    bind<Tracker>() with instance(run {
        val analytics = GoogleAnalytics.getInstance(app)
        val tracker = analytics.newTracker(PROPERTY_ID)
        tracker.enableAdvertisingIdCollection(true)
        tracker.enableAutoActivityTracking(true)
        tracker.enableExceptionReporting(true)
        tracker.setAppVersion(BuildConfig.VERSION_NAME)
        tracker
    })
}
