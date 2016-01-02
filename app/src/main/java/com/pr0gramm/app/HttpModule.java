package com.pr0gramm.app;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.common.base.Stopwatch;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.api.pr0gramm.ApiProvider;
import com.pr0gramm.app.api.pr0gramm.LoginCookieHandler;
import com.pr0gramm.app.services.proxy.HttpProxyService;
import com.pr0gramm.app.services.proxy.ProxyService;
import com.pr0gramm.app.util.GuavaPicassoCache;
import com.pr0gramm.app.util.SmallBufferSocketFactory;
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

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 */
@Module
public class HttpModule {
    private static final Logger logger = LoggerFactory.getLogger("HttpModule");

    @Provides
    @Singleton
    public OkHttpClient okHttpClient(Context context, Settings settings, LoginCookieHandler cookieHandler) {
        File cacheDir = new File(context.getCacheDir(), "imgCache");

        OkHttpClient client = new OkHttpClient();
        client.setCache(new Cache(cacheDir, 256 * 1024 * 1024));
        client.setCookieHandler(cookieHandler);
        client.setSocketFactory(new SmallBufferSocketFactory());

        client.setReadTimeout(15, TimeUnit.SECONDS);
        client.setWriteTimeout(15, TimeUnit.SECONDS);
        client.setConnectTimeout(10, TimeUnit.SECONDS);
        client.setConnectionPool(new ConnectionPool(4, 1000));
        client.setRetryOnConnectionFailure(true);

        client.setProxySelector(settings.useApiProxy() ? new CustomProxySelector() : null);

        final Logger logger = LoggerFactory.getLogger("OkHttpClient");
        client.networkInterceptors().add(chain -> {
            Stopwatch watch = Stopwatch.createStarted();
            Request request = chain.request();

            logger.info("performing {} http request for {}", request.method(), request.urlString());
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

        logger.warn("Stop trying, using no proxy now.");
        return result;
    }

    @Provides
    @Singleton
    public Picasso picasso(Context context, Downloader downloader) {
        return new Picasso.Builder(context)
                .defaultBitmapConfig(Bitmap.Config.RGB_565)
                .memoryCache(GuavaPicassoCache.defaultSizedGuavaCache())
                .downloader(downloader)
                .build();
    }

    @Provides
    public Api api(ApiProvider apiProvider) {
        return apiProvider.get();
    }
}
