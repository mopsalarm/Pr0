package com.pr0gramm.app

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.StrictMode
import android.support.multidex.MultiDex
import com.github.anrwatchdog.ANRWatchDog
import com.pr0gramm.app.util.subscribeOnBackground
import rx.Observable
import java.util.concurrent.TimeUnit


class DebugApplicationClass : ApplicationClass() {
    init {
        StrictMode.enableDefaults()

        if (false) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Debug.startMethodTracing(null, 128 * 1024 * 1024)
                Debug.startMethodTracingSampling(null, 16 * 1024 * 1042, 100)

                Observable.fromCallable { Debug.stopMethodTracing() }
                        .delaySubscription(4, TimeUnit.SECONDS)
                        .subscribeOnBackground()
                        .subscribe()
            }
        }

        ANRWatchDog(5000)
                .setIgnoreDebugger(true)
                .setReportMainThreadOnly()
                .setANRListener { err -> err.printStackTrace() }
                .start()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}
