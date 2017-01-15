package com.pr0gramm.app;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.Uninterruptibles;
import com.jakewharton.picasso.OkHttp3Downloader;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.api.pr0gramm.ApiProvider;
import com.pr0gramm.app.api.pr0gramm.LoginCookieHandler;
import com.pr0gramm.app.services.proxy.HttpProxyService;
import com.pr0gramm.app.services.proxy.ProxyService;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.GuavaPicassoCache;
import com.pr0gramm.app.util.SmallBufferSocketFactory;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import okhttp3.Cache;
import okhttp3.ConnectionPool;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static com.google.common.base.Preconditions.checkState;
import static com.pr0gramm.app.util.Noop.noop;

/**
 */
@Module
public class HttpModule {
    private static final Logger logger = LoggerFactory.getLogger("HttpModule");

    @Provides
    @Singleton
    public OkHttpClient okHttpClient(Context context, LoginCookieHandler cookieHandler) {
        final Logger okLogger = LoggerFactory.getLogger("OkHttpClient");

        File cacheDir = new File(context.getCacheDir(), "imgCache");

        int version = AndroidUtility.buildVersionCode();
        return new OkHttpClient.Builder()
                .cache(new Cache(cacheDir, 64 * 1024 * 1024))
                .socketFactory(new SmallBufferSocketFactory())

                .cookieJar(cookieHandler)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(8, 30, TimeUnit.SECONDS))
                .retryOnConnectionFailure(true)

                .addInterceptor(BuildConfig.DEBUG ? new DebugInterceptor() : noop)

                .addInterceptor(new DoNotCacheInterceptor("vid.pr0gramm.com", "img.pr0gramm.com", "full.pr0gramm.com"))
                .addNetworkInterceptor(new UserAgentInterceptor("pr0gramm-app/v" + version))
                .addNetworkInterceptor(BuildConfig.DEBUG ? StethoWrapper.networkInterceptor() : noop)

                .addNetworkInterceptor(chain -> {
                    Stopwatch watch = Stopwatch.createStarted();
                    Request request = chain.request();

                    okLogger.info("performing {} http request for {}", request.method(), request.url());
                    try {
                        Response response = chain.proceed(request);
                        okLogger.info("{} ({}) took {}", request.url(), response.code(), watch);
                        return response;

                    } catch (Exception error) {
                        okLogger.warn("{} produced error: {}", request.url(), error);
                        throw error;
                    }
                })
                .build();
    }

    @Provides
    @Singleton
    public Downloader downloader(OkHttpClient client, com.pr0gramm.app.io.Cache cache) {
        OkHttp3Downloader fallback = new OkHttp3Downloader(client);

        return new Downloader() {
            private final Logger logger = LoggerFactory.getLogger("Picasso.Downloader");

            @TargetApi(Build.VERSION_CODES.KITKAT)
            @Override
            public Response load(Uri uri, int networkPolicy) throws IOException {
                // load thumbnails normally
                if (uri.getHost().contains("thumb.pr0gramm.com") || uri.getPath().contains("/thumb.jpg")) {
                    return fallback.load(uri, networkPolicy);
                } else {
                    logger.debug("Using cache to download image {}", uri);
                    try (com.pr0gramm.app.io.Cache.Entry entry = cache.entryOf(uri)) {
                        boolean fullyCached = entry.fractionCached() == 1;
                        return new Response(entry.inputStreamAt(0), fullyCached, entry.totalSize());
                    }
                }
            }

            @Override
            public void shutdown() {
                fallback.shutdown();
            }
        };
    }

    @Provides
    @Singleton
    public ProxyService proxyService(com.pr0gramm.app.io.Cache cache) {
        for (int i = 0; i < 10; i++) {
            try {
                HttpProxyService proxy = new HttpProxyService(cache);
                proxy.start();

                // return the proxy
                return proxy;

            } catch (IOException ioError) {
                logger.warn("Could not open proxy: {}", ioError.toString());
            }
        }

        logger.warn("Stop trying, using no proxy now.");
        return url -> url;
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

    private static class DebugInterceptor implements Interceptor {
        DebugInterceptor() {
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            checkState(BuildConfig.DEBUG, "Must only be used in a debug build");

            Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
            return chain.proceed(chain.request());
        }
    }

    private static class UserAgentInterceptor implements Interceptor {
        private final String userAgent;

        UserAgentInterceptor(String userAgent) {
            this.userAgent = userAgent;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request().newBuilder()
                    .header(HttpHeaders.USER_AGENT, userAgent)
                    .build();

            return chain.proceed(request);
        }
    }

    private static class DoNotCacheInterceptor implements Interceptor {
        private static final Logger logger = LoggerFactory.getLogger("DoNotCacheInterceptor");

        private final ImmutableSet<String> domains;

        DoNotCacheInterceptor(String... domains) {
            this.domains = ImmutableSet.copyOf(domains);
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Response response = chain.proceed(request);

            if (domains.contains(request.url().host())) {
                logger.info("Disable caching for {}", request.url());
                response.header("Cache-Control", "no-store");
            }

            return response;
        }
    }
}
