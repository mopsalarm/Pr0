package com.pr0gramm.app

import android.content.Context
import android.support.multidex.MultiDex
import com.facebook.stetho.Stetho
import com.facebook.stetho.okhttp3.StethoInterceptor
import okhttp3.Interceptor


class DebugApplicationClass : ApplicationClass() {
    override fun onCreate() {
        super.onCreate()

        Stetho.initializeWithDefaults(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun debugNetworkInterceptor(): Interceptor = StethoInterceptor()
}
