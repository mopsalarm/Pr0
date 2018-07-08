package com.pr0gramm.app

import android.app.Application
import com.google.firebase.analytics.FirebaseAnalytics
import org.kodein.di.Kodein
import org.kodein.di.erased.bind
import org.kodein.di.erased.instance

/**
 */
fun trackingModule(app: Application) = Kodein.Module("tracking") {
    bind<FirebaseAnalytics>() with instance(FirebaseAnalytics.getInstance(app).apply {
        setAnalyticsCollectionEnabled(true)
    })
}
