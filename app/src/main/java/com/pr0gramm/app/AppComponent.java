package com.pr0gramm.app;

import android.app.DownloadManager;
import android.content.SharedPreferences;

import com.pr0gramm.app.services.DownloadCompleteReceiver;
import com.pr0gramm.app.services.DownloadUpdateReceiver;
import com.pr0gramm.app.services.NotificationService;
import com.pr0gramm.app.services.ShareProvider;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.services.preloading.PreloadManager;
import com.pr0gramm.app.services.preloading.PreloadService;
import com.pr0gramm.app.sync.SyncIntentService;
import com.pr0gramm.app.ui.SettingsActivity;
import com.squareup.picasso.Picasso;

import javax.inject.Singleton;

import dagger.Component;

/**
 */
@Singleton
@Component(modules = {
        AppModule.class,
        HttpModule.class,
        ServicesModule.class,
})
public interface AppComponent {
    ActivityComponent activiyComponent(ActivityModule activityModule);

    PreloadManager preloadManager();

    UserService userService();

    SharedPreferences sharedPreferences();

    Picasso picasso();

    void inject(SyncIntentService service);

    void inject(SettingsActivity.SettingsFragment fragment);

    void inject(DownloadCompleteReceiver downloadCompleteReceiver);

    void inject(ShareProvider shareProvider);

    void inject(PreloadService preloadService);

    void inject(DownloadUpdateReceiver downloadUpdateReceiver);

    DownloadManager downloadManager();

    NotificationService notificationService();
}
