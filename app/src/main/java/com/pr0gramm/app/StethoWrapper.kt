package com.pr0gramm.app

import android.content.Context
import okhttp3.Interceptor
import org.slf4j.LoggerFactory

/**
 * Wraps the stetho stuff.
 */
object StethoWrapper {
    private val logger = LoggerFactory.getLogger("StethoWrapper")

    fun init(applicationClass: ApplicationClass) {
        try {
            // invoke Stetho.init(applicationClass)
            val stethoClass = Class.forName("com.facebook.stetho.Stetho")
            val init = stethoClass.getMethod("initializeWithDefaults", Context::class.java)
            init.invoke(null, applicationClass)

        } catch (err: Exception) {
            logger.warn("Could not initialize stetho: " + err)
        }

    }

    fun networkInterceptor(): Interceptor {
        try {
            if (!BuildConfig.DEBUG) {
                val interceptorClass = Class.forName("com.facebook.stetho.okhttp3.StethoInterceptor") as Class<Interceptor>
                return interceptorClass.newInstance()
            }

        } catch (err: Exception) {
            logger.warn("Could not get stetho network interceptor: " + err)
        }

        return Interceptor { it.proceed(it.request()) }
    }
}
