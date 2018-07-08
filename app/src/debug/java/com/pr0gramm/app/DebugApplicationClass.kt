package com.pr0gramm.app

import android.content.Context
import android.support.multidex.MultiDex


class DebugApplicationClass : ApplicationClass() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}
