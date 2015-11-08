package com.pr0gramm.app;

import android.content.Context;
import android.content.pm.PackageManager;

import com.google.common.base.Stopwatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This service checks if unlocking is needed and if the unlock plugin is installed.
 */
@Singleton
public class UnlockService {
    private static final Logger logger = LoggerFactory.getLogger(UnlockService.class);

    private final Context context;

    @Inject
    public UnlockService(Context context) {
        this.context = context;
    }

    public boolean unlocked() {
        Stopwatch watch = Stopwatch.createStarted();
        try {
            return pluginNotRequired() || appInstalled(context, "io.github.mopsalarm.pr0gramm.unlock");
        } finally {
            logger.info("Checking for unlock app took {}", watch);
        }
    }

    private static boolean pluginNotRequired() {
        //noinspection PointlessBooleanExpression
        return !BuildConfig.REQUIRES_UNLOCK_PLUGIN;
    }

    private static boolean appInstalled(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            // pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            pm.getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }
}
