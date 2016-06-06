package com.pr0gramm.app;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.common.base.Stopwatch;
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
    public OkHttpClient okHttpClient(Context context, Settings settings, LoginCookieHandler cookieHandler) {
        final Logger okLogger = LoggerFactory.getLogger("OkHttpClient");

        File cacheDir = new File(context.getCacheDir(), "imgCache");

        int version = AndroidUtility.getPackageVersionCode(context);
        return new OkHttpClient.Builder()
                .cache(new Cache(cacheDir, 256 * 1024 * 1024))
                .socketFactory(new SmallBufferSocketFactory())

                .cookieJar(cookieHandler)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(4, 3, TimeUnit.SECONDS))
                .retryOnConnectionFailure(true)

                .addInterceptor(BuildConfig.DEBUG ? new DebugInterceptor() : noop)

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
    public Downloader downloader(OkHttpClient client) {
        return new OkHttp3Downloader(client);
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
}
