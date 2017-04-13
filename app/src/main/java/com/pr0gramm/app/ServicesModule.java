package com.pr0gramm.app;

import com.pr0gramm.app.services.preloading.DatabasePreloadManager;
import com.pr0gramm.app.services.preloading.PreloadManager;

import dagger.Module;
import dagger.Provides;

/**
 */
@Module
public class ServicesModule {
    @Provides
    public PreloadManager preloadManager(DatabasePreloadManager preloadManager) {
        return preloadManager;
    }
}
