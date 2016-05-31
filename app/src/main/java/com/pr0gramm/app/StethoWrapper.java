package com.pr0gramm.app;

import android.content.Context;

import com.pr0gramm.app.util.Noop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

import okhttp3.Interceptor;

/**
 * Wraps the stetho stuff.
 */
public class StethoWrapper {
    private static final Logger logger = LoggerFactory.getLogger("StethoWrapper");

    public static void init(ApplicationClass applicationClass) {
        try {
            // invoke Stetho.init(applicationClass)
            Class<?> stethoClass = Class.forName("com.facebook.stetho.Stetho");
            Method init = stethoClass.getMethod("initializeWithDefaults", Context.class);
            init.invoke(null, applicationClass);

        } catch (Exception err) {
            logger.warn("Could not initialize stetho: " + err);
        }
    }

    public static Interceptor networkInterceptor() {
        try {
            //noinspection unchecked
            Class<Interceptor> interceptorClass = (Class<Interceptor>) Class.forName("com.facebook.stetho.okhttp3.StethoInterceptor");
            return interceptorClass.newInstance();

        } catch (Exception err) {
            logger.warn("Could not get stetho network interceptor: " + err);
            return Noop.noop;
        }
    }
}
