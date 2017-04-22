package com.pr0gramm.app

import com.facebook.stetho.Stetho
import com.facebook.stetho.okhttp3.StethoInterceptor
import okhttp3.Interceptor

class DebugApplicationClass : ApplicationClass() {
    override fun onCreate() {
        super.onCreate()

        Stetho.initializeWithDefaults(this)
    }

    override fun debugNetworkInterceptor(): Interceptor = StethoInterceptor()
}
