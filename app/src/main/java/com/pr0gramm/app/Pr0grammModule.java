package com.pr0gramm.app;

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.google.common.base.Stopwatch;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.services.DatabasePreloadManager;
import com.pr0gramm.app.services.GifToVideoService;
import com.pr0gramm.app.services.HttpProxyService;
import com.pr0gramm.app.services.MyGifToVideoService;
import com.pr0gramm.app.services.PreloadManager;
import com.pr0gramm.app.services.ProxyService;
import com.pr0gramm.app.services.RestAdapterProvider;
import com.squareup.okhttp.Cache;
import com.squareup.okhttp.ConnectionPool;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.OkHttpDownloader;
import com.squareup.picasso.Picasso;
import com.squareup.sqlbrite.BriteDatabase;
import com.squareup.sqlbrite.SqlBrite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import roboguice.inject.SharedPreferencesName;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

/**
 */
@SuppressWarnings("UnusedDeclaration")
public class Pr0grammModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(Pr0grammModule.class);

    @Override
    protected void configure() {
        bind(Api.class).toProvider(RestAdapterProvider.class);
        bind(PreloadManager.class).to(DatabasePreloadManager.class);
        bind(GifToVideoService.class).to(MyGifToVideoService.class);
    }

    @Provides
    @Singleton
    public Downloader downloader(OkHttpClient client) {
        return new OkHttpDownloader(client);
    }

    @Provides
    @Singleton
    public OkHttpClient okHttpClient(Context context, LoginCookieHandler cookieHandler) throws IOException {
        File cacheDir = new File(context.getCacheDir(), "imgCache");

        OkHttpClient client = new OkHttpClient();
        client.setCache(new Cache(cacheDir, 100 * 1024 * 1024));
        client.setCookieHandler(cookieHandler);
        client.setSocketFactory(new SmallBufferSocketFactory());

        client.setReadTimeout(15, TimeUnit.SECONDS);
        client.setWriteTimeout(15, TimeUnit.SECONDS);
        client.setConnectTimeout(10, TimeUnit.SECONDS);
        client.setConnectionPool(new ConnectionPool(4, 1000));
        client.setRetryOnConnectionFailure(true);

        final Logger logger = LoggerFactory.getLogger(OkHttpClient.class);
        client.networkInterceptors().add(chain -> {
            Stopwatch watch = Stopwatch.createStarted();
            Request request = chain.request();

            logger.info("performing http request for " + request.urlString());
            try {
                Response response = chain.proceed(request);
                logger.info("{} ({}) took {}", request.urlString(), response.code(), watch);
                return response;

            } catch (Exception error) {
                logger.warn("{} produced error: {}", request.urlString(), error);
                throw error;
            }
        });

        return client;
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
    public ProxyService proxyService(Settings settings, OkHttpClient httpClient) {
        ProxyService result = url -> url;

        for (int i = 0; i < 10; i++) {
            try {
                HttpProxyService proxy = new HttpProxyService(httpClient);
                proxy.start();

                // return the proxy
                result = url -> settings.useProxy() ? proxy.proxy(url) : url;

            } catch (IOException ioError) {
                logger.warn("Could not open proxy: {}", ioError.toString());
            }
        }

        return result;
    }

    @Provides
    @Singleton
    public Observable<BriteDatabase> sqlBrite(Application application) {
        return Async.start(() -> {
            SQLiteOpenHelper openHelper = new OpenHelper(application);
            return SqlBrite.create().wrapDatabaseHelper(openHelper);
        }, Schedulers.io());
    }

    @Provides
    @SharedPreferencesName
    public String sharedPreferencesName() {
        return "pr0gramm";
    }

    private static class OpenHelper extends SQLiteOpenHelper {
        public OpenHelper(Context context) {
            super(context, "pr0-sqlbrite", null, 4);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            DatabasePreloadManager.onCreate(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onCreate(db);
        }
    }
}
