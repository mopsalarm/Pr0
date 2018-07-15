package com.pr0gramm.app

import android.content.Context
import android.os.StrictMode
import android.support.multidex.MultiDex


class DebugApplicationClass : ApplicationClass() {
    init {
        StrictMode.enableDefaults()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}
