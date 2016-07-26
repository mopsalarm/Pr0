package com.pr0gramm.app;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.AsyncTask;

import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.services.VoteService;
import com.pr0gramm.app.services.preloading.DatabasePreloadManager;
import com.pr0gramm.app.services.proxy.ProxyService;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;
import com.pr0gramm.app.util.AndroidUtility;

import javax.inject.Inject;

import proguard.annotation.KeepClassMembers;

/**
 * Provides dagger injection points/components
 */
public class Dagger {
    private Dagger() {
    }

    public static AppComponent appComponent(Context context) {
        return ApplicationClass.get(context).appComponent.get();
    }

    public static ActivityComponent activityComponent(Activity activity) {
        if (activity instanceof BaseAppCompatActivity) {
            // create or reuse the graph
            return ((BaseAppCompatActivity) activity).getActivityComponent();
        } else {
            return newActivityComponent(activity);
        }
    }

    public static ActivityComponent newActivityComponent(Activity activity) {
        return appComponent(activity).activiyComponent(new ActivityModule(activity));
    }

    public static void initEagerSingletons(Application application) {
        AsyncTask.execute(() -> {
            try {
                Dagger.appComponent(application).inject(new EagerSingletons());
            } catch (Throwable error) {
                AndroidUtility.logToCrashlytics(error);
            }
        });
    }

    @KeepClassMembers
    static class EagerSingletons {
        private EagerSingletons() {
        }

        @Inject
        ProxyService proxyService;

        @Inject
        DatabasePreloadManager databasePreloadManager;

        @Inject
        VoteService voteService;

        @Inject
        UserService userService;
    }
}
