package com.pr0gramm.app;

import android.content.Context;

import com.google.common.reflect.Reflection;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.pr0gramm.app.api.InstantDeserializer;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.services.GifToVideoService;
import com.pr0gramm.app.services.MyGifToVideoService;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.OkHttpDownloader;
import com.squareup.picasso.Picasso;

import org.joda.time.Instant;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.CookieHandler;
import java.util.Arrays;

import retrofit.RestAdapter;
import retrofit.android.AndroidLog;
import retrofit.client.OkClient;
import retrofit.converter.GsonConverter;
import roboguice.inject.SharedPreferencesName;

/**
 */
@SuppressWarnings("UnusedDeclaration")
public class Pr0grammModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(CookieHandler.class).to(LoginCookieHandler.class);
    }

    @Provides
    @Singleton
    public Api api(Settings settings, LoginCookieHandler cookieHandler) {
        OkHttpClient client = new OkHttpClient();
        client.setCookieHandler(cookieHandler);

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantDeserializer())
                .create();

        Api api = new RestAdapter.Builder()
                .setEndpoint("http://pr0gramm.com")
                .setConverter(new GsonConverter(gson))
                .setLogLevel(RestAdapter.LogLevel.BASIC)
                .setLog(new AndroidLog("Pr0grammApi"))
                .setClient(new OkClient(client))
                .build()
                .create(Api.class);

        // proxy to add the nounce if not provided
        return Reflection.newProxy(Api.class, (proxy, method, args) -> {
            Class<?>[] params = method.getParameterTypes();
            if (params.length > 0 && params[0] == Api.Nonce.class) {
                if(args.length > 0 && args[0] == null) {
                    args = Arrays.copyOf(args, args.length);
                    args[0] = cookieHandler.getNounce();
                }
            }

            // forward method call
            try {
                return method.invoke(api, args);
            } catch(InvocationTargetException ierr) {
                throw ierr.getCause();
            }
        });
    }

    @Provides
    @Singleton
    public GifToVideoService gifToVideoService() {
        return new MyGifToVideoService();
    }

    @Provides
    @Singleton
    public Downloader downloader(Context context) {
        File cache = new File(context.getCacheDir(), "imgCache");
        return new OkHttpDownloader(cache);
    }

    @Provides
    @Singleton
    public Picasso picasso(Context context, Downloader downloader) {
        return new Picasso.Builder(context)
                .memoryCache(GuavaPicassoCache.defaultSizedGuavaCache())
                .downloader(downloader)
                .build();
    }

    @Provides
    @SharedPreferencesName
    public String sharedPreferencesName() {
        return "pr0gramm";
    }
}
