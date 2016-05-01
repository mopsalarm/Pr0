package com.pr0gramm.app;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pr0gramm.app.api.InstantTypeAdapter;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersAccountInfo;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersComment;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersFeed;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersInfo;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersInvited;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersLogin;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersMessage;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersMessageFeed;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersNewComment;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersNewTag;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersPost;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersPosted;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersPrivateMessage;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersPrivateMessageFeed;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersSync;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersTag;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersThemeInfo;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersUpload;
import com.pr0gramm.app.api.pr0gramm.response.GsonAdaptersUserComments;
import com.pr0gramm.app.services.GsonAdaptersUpdate;

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
            .registerTypeAdapterFactory(new GsonAdaptersTag())
            .registerTypeAdapterFactory(new GsonAdaptersComment())
            .registerTypeAdapterFactory(new GsonAdaptersFeed())
            .registerTypeAdapterFactory(new GsonAdaptersUpload())
            .registerTypeAdapterFactory(new GsonAdaptersUserComments())
            .registerTypeAdapterFactory(new GsonAdaptersPrivateMessage())
            .registerTypeAdapterFactory(new GsonAdaptersPrivateMessageFeed())
            .registerTypeAdapterFactory(new GsonAdaptersPosted())
            .registerTypeAdapterFactory(new GsonAdaptersLogin())
            .registerTypeAdapterFactory(new GsonAdaptersMessage())
            .registerTypeAdapterFactory(new GsonAdaptersNewComment())
            .registerTypeAdapterFactory(new GsonAdaptersNewTag())
            .registerTypeAdapterFactory(new GsonAdaptersPost())
            .registerTypeAdapterFactory(new GsonAdaptersSync())
            .registerTypeAdapterFactory(new GsonAdaptersMessageFeed())
            .registerTypeAdapterFactory(new GsonAdaptersAccountInfo())
            .registerTypeAdapterFactory(new GsonAdaptersInfo())
            .registerTypeAdapterFactory(new GsonAdaptersUpdate())
            .registerTypeAdapterFactory(new GsonAdaptersThemeInfo())
            .registerTypeAdapterFactory(new GsonAdaptersInvited())
            .create();

    @Singleton
    @Provides
    public Gson gson() {
        return INSTANCE;
    }
}
