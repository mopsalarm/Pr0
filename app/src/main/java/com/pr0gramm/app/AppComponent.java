package com.pr0gramm.app;

import android.content.SharedPreferences;

import com.google.android.gms.analytics.Tracker;
import com.pr0gramm.app.services.InboxNotificationCanceledReceiver;
import com.pr0gramm.app.services.MessageReplyReceiver;
import com.pr0gramm.app.services.SettingsTrackerService;
import com.pr0gramm.app.services.ShareProvider;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.services.preloading.PreloadService;
import com.pr0gramm.app.sync.SyncIntentService;
import com.pr0gramm.app.ui.SettingsActivity;
import com.pr0gramm.app.ui.views.CommentPostLine;

import javax.inject.Singleton;

import dagger.Component;

/**
 */
@Singleton
@Component(modules = {
        AppModule.class,
        TrackingModule.class,
        HttpModule.class,
        ServicesModule.class,
        GsonModule.class,
})
public interface AppComponent {
    ActivityComponent activiyComponent(ActivityModule activityModule);

    UserService userService();

    SharedPreferences sharedPreferences();

    Tracker googleAnalytics();

    Tracker googleAnalyticsTracker();

    SettingsTrackerService settingsTracker();

    void inject(SyncIntentService service);

    void inject(SettingsActivity.SettingsFragment fragment);

    void inject(ShareProvider shareProvider);

    void inject(PreloadService preloadService);

    void inject(Dagger.EagerSingletons eagerSingletons);

    void inject(InboxNotificationCanceledReceiver receiver);

    void inject(CommentPostLine commentPostLine);

    void inject(MessageReplyReceiver receiver);

    void inject(KApp kApp);
}
