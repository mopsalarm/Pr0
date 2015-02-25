package com.pr0gramm.app;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.pr0gramm.app.api.Api;
import com.pr0gramm.app.api.InstantDeserializer;

import org.joda.time.Instant;

import retrofit.RestAdapter;
import retrofit.converter.GsonConverter;

/**
 */
@SuppressWarnings("UnusedDeclaration")
public class Pr0grammModule extends AbstractModule {
    @Override
    protected void configure() {

    }

    @Provides
    @Singleton
    public Gson gson() {
        return new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantDeserializer())
                .create();
    }

    @Provides
    @Singleton
    public RestAdapter restAdapter(Gson gson) {
        return new RestAdapter.Builder()
                .setEndpoint("http://pr0gramm.com")
                .setConverter(new GsonConverter(gson))
                .setLogLevel(RestAdapter.LogLevel.HEADERS_AND_ARGS)
                .build();
    }

    @Provides
    @Singleton
    public Api api(RestAdapter restAdapter) {
        return restAdapter.create(Api.class);
    }
}
