package com.pr0gramm.app

import android.content.Context
import android.os.StrictMode
import android.support.multidex.MultiDex
import com.github.anrwatchdog.ANRWatchDog


class DebugApplicationClass : ApplicationClass() {
    init {
        StrictMode.enableDefaults()

        ANRWatchDog(2000)
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
