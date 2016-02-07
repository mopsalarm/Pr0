package com.pr0gramm.app;

import android.content.SharedPreferences;

import com.pr0gramm.app.api.meta.MetaService;
import com.pr0gramm.app.services.DownloadService;
import com.pr0gramm.app.services.InboxNotificationCanceledReceiver;
import com.pr0gramm.app.services.NotificationService;
import com.pr0gramm.app.services.ShareProvider;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.services.preloading.PreloadManager;
import com.pr0gramm.app.services.preloading.PreloadService;
import com.pr0gramm.app.sync.SyncIntentService;
import com.pr0gramm.app.ui.SettingsActivity;
import com.squareup.picasso.Picasso;

import javax.inject.Singleton;

import dagger.Component;
import okhttp3.OkHttpClient;

/**
 */
@Singleton
@Component(modules = {
        AppModule.class,
        HttpModule.class,
        ServicesModule.class,
        GsonModule.class,
})
public interface AppComponent {
    ActivityComponent activiyComponent(ActivityModule activityModule);

    PreloadManager preloadManager();

    UserService userService();

    SharedPreferences sharedPreferences();

    Picasso picasso();

    NotificationService notificationService();

    SingleShotService singleShotService();

    MetaService metaService();

    OkHttpClient okHttpClient();

    DownloadService downloadService();

    void inject(SyncIntentService service);

    void inject(SettingsActivity.SettingsFragment fragment);

    void inject(ShareProvider shareProvider);

    void inject(PreloadService preloadService);

    void inject(Dagger.EagerSingletons eagerSingletons);

    void inject(InboxNotificationCanceledReceiver receiver);
}
