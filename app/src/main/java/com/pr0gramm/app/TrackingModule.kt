package com.pr0gramm.app

import android.app.Application
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.perf.FirebasePerformance

/**
 */
fun trackingModule(app: Application) = Kodein.Module {
    bind<FirebaseAnalytics>() with instance(FirebaseAnalytics.getInstance(app).apply {
        setAnalyticsCollectionEnabled(true)
    })

    bind<FirebasePerformance>() with instance(FirebasePerformance.getInstance().apply {
        isPerformanceCollectionEnabled = true
    })
}
