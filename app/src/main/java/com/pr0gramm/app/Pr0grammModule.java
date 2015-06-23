package com.pr0gramm.app;

import android.content.Context;

import com.google.common.base.Stopwatch;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.services.GifToVideoService;
import com.pr0gramm.app.services.HttpProxyService;
import com.pr0gramm.app.services.MyGifToVideoService;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import roboguice.inject.SharedPreferencesName;

import static org.joda.time.Duration.standardSeconds;

/**
 */
@SuppressWarnings("UnusedDeclaration")
public class Pr0grammModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(Pr0grammModule.class);

    @Override
    protected void configure() {
        bind(Api.class).toProvider(RestAdapterProvider.class);
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
        client.setSocketFactory(new SmallBufferSocketFactory());

        client.setReadTimeout(15, TimeUnit.SECONDS);
        client.setWriteTimeout(15, TimeUnit.SECONDS);
        client.setConnectTimeout(10, TimeUnit.SECONDS);
        client.setConnectionPool(new ConnectionPool(4, standardSeconds(2).getMillis()));
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
    public ProxyService proxyService(Settings settings, OkHttpClient httpClient) {
        for (int i = 0; i < 10; i++) {
            try {
                HttpProxyService proxy = new HttpProxyService(httpClient);
                proxy.start();

                // return the proxy
                return url -> settings.useProxy() ? proxy.proxy(url) : url;

            } catch (IOException ioError) {
                logger.warn("Could not open proxy: {}", ioError.toString());
            }
        }

        // if we could not open a proxy, just go with no proxy.
        return url -> url;
    }

    @Provides
    @SharedPreferencesName
    public String sharedPreferencesName() {
        return "pr0gramm";
    }
}
