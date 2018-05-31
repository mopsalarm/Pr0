package com.pr0gramm.app;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pr0gramm.app.api.InstantTypeAdapter;

import org.joda.time.Instant;

/**
 * Provide ALL the gson stuff!
 */
public class GsonModule {
    public static final Gson INSTANCE = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantTypeAdapter().nullSafe())
            .registerTypeAdapterFactory(new com.pr0gramm.app.services.config.GsonAdaptersConfig())
            .create();
}
