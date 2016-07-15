package com.pr0gramm.app.services;

import android.content.Context;
import android.os.AsyncTask;

import com.pr0gramm.app.util.AndroidUtility;

import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Singleton;

import me.leolin.shortcutbadger.ShortcutBadger;

/**
 * Service to wrap the badger class and allow only single threaded
 * access because of threading issues with the api :/
 */
@Singleton
public class BadgeService {
    private final Executor executor = AsyncTask.SERIAL_EXECUTOR;

    @Inject
    public BadgeService() {
    }

    public void update(Context context, int badgeCount) {
        Context appContext = context.getApplicationContext();
        executor.execute(() -> updateInternal(appContext, badgeCount));
    }

    public void reset(Context context) {
        update(context, 0);
    }

    private void updateInternal(Context appContext, int badgeCount) {
        try {
            ShortcutBadger.applyCount(appContext, badgeCount);

        } catch (Throwable err) {
            AndroidUtility.logToCrashlytics(err);
        }
    }
}
