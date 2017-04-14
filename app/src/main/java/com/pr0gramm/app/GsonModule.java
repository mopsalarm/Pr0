package com.pr0gramm.app;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pr0gramm.app.api.InstantTypeAdapter;
import com.pr0gramm.app.api.pr0gramm.GsonAdaptersApi;
import com.pr0gramm.app.services.UserService;

import org.joda.time.Instant;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Provide ALL the gson stuff!
 */
@Module
public class GsonModule {
    public static final Gson INSTANCE = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantTypeAdapter().nullSafe())
            .registerTypeAdapter(UserService.LoginState.class, new UserService.LoginStateAdapter())
            .registerTypeAdapterFactory(new GsonAdaptersApi())
            .create();

    @Singleton
    @Provides
    public Gson gson() {
        return INSTANCE;
    }
}
