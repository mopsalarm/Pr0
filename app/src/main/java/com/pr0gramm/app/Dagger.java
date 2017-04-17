package com.pr0gramm.app;

import android.os.AsyncTask;

import com.github.salomonbrys.kodein.Kodein;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.services.VoteService;
import com.pr0gramm.app.services.preloading.PreloadManager;
import com.pr0gramm.app.services.proxy.ProxyService;
import com.pr0gramm.app.util.AndroidUtility;

/**
 * Provides dagger injection points/components
 */
public class Dagger {
    private Dagger() {
    }

    static void initEagerSingletons(Kodein kodein) {
        AsyncTask.execute(() -> {
            try {
                kodein.getTyped().instance(ProxyService.class);
                kodein.getTyped().instance(PreloadManager.class);
                kodein.getTyped().instance(VoteService.class);
                kodein.getTyped().instance(UserService.class);
            } catch (Throwable error) {
                AndroidUtility.logToCrashlytics(error);
            }
        });
    }
}
