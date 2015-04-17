package com.pr0gramm.app;

import android.content.Context;
import android.util.Log;

import com.google.common.base.Stopwatch;
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
import com.pr0gramm.app.services.SimpleProxyService;
import com.squareup.okhttp.Cache;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.OkHttpDownloader;
import com.squareup.picasso.Picasso;

import org.joda.time.Instant;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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
    }

    @Provides
    @Singleton
    public Api api(Settings settings, LoginCookieHandler cookieHandler, OkHttpClient client) {
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

        // proxy to add the nonce if not provided
        return Reflection.newProxy(Api.class, (proxy, method, args) -> {
            Class<?>[] params = method.getParameterTypes();
            if (params.length > 0 && params[0] == Api.Nonce.class) {
                if (args.length > 0 && args[0] == null) {
                    // inform about failure.
                    if (!cookieHandler.getCookie().isPresent())
                        throw new LoginRequiredException(method.toString());

                    args = Arrays.copyOf(args, args.length);
                    args[0] = cookieHandler.getNonce();
                }
            }

            // forward method call
            try {
                return method.invoke(api, args);
            } catch (InvocationTargetException err) {
                throw err.getCause();
            }
        });
    }

    @Provides
    @Singleton
    public GifToVideoService gifToVideoService(OkHttpClient client) {
        return new MyGifToVideoService(client);
    }

    @Provides
    @Singleton
    public OkHttpClient okHttpClient(Context context, LoginCookieHandler cookieHandler) throws IOException {
        File cacheDir = new File(context.getCacheDir(), "imgCache");

        OkHttpClient client = new OkHttpClient();
        client.setCache(new Cache(cacheDir, 100 * 1024 * 1024));
        client.setCookieHandler(cookieHandler);

        client.networkInterceptors().add(chain -> {
            Stopwatch watch = Stopwatch.createStarted();
            Request request = chain.request();

            Log.i("HttpClient", "performing http request for " + request.urlString());
            Response response = chain.proceed(request);

            Log.i("HttpClient", String.format("%s (%d) took %s",
                    request.urlString(), response.code(), watch));

            return response;
        });

        return client;
    }

    @Provides
    @Singleton
    public Downloader downloader(OkHttpClient client) {
        return new OkHttpDownloader(client);
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
    @Singleton
    public SimpleProxyService proxyService(OkHttpClient httpClient) throws IOException {
        SimpleProxyService proxy = new SimpleProxyService(httpClient);
        proxy.start();

        return proxy;
    }

    @Provides
    @SharedPreferencesName
    public String sharedPreferencesName() {
        return "pr0gramm";
    }
}
