package com.pr0gramm.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.github.salomonbrys.kodein.singleton
import com.google.common.base.Stopwatch
import com.google.common.net.HttpHeaders
import com.google.common.util.concurrent.Uninterruptibles
import com.jakewharton.picasso.OkHttp3Downloader
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.ApiProvider
import com.pr0gramm.app.api.pr0gramm.LoginCookieHandler
import com.pr0gramm.app.services.proxy.HttpProxyService
import com.pr0gramm.app.services.proxy.ProxyService
import com.pr0gramm.app.util.AndroidUtility.checkNotMainThread
import com.pr0gramm.app.util.GuavaPicassoCache
import com.pr0gramm.app.util.SmallBufferSocketFactory
import com.squareup.picasso.Downloader
import com.squareup.picasso.Picasso
import okhttp3.*
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 */
fun httpModule(app: Application) = Kodein.Module {
    bind<LoginCookieHandler>() with singleton { LoginCookieHandler(instance()) }

    bind<OkHttpClient>() with singleton {
        val cookieHandler = instance<LoginCookieHandler>()

        val cacheDir = File(app.cacheDir, "imgCache")

        OkHttpClient.Builder()
                .cache(Cache(cacheDir, (64 * 1024 * 1024).toLong()))
                .socketFactory(SmallBufferSocketFactory())

                .cookieJar(cookieHandler)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(8, 30, TimeUnit.SECONDS))
                .retryOnConnectionFailure(true)

                .addNetworkInterceptor(DebugInterceptor())

                .addInterceptor(DoNotCacheInterceptor("vid.pr0gramm.com", "img.pr0gramm.com", "full.pr0gramm.com"))
                .addNetworkInterceptor(UserAgentInterceptor("pr0gramm-app/v" + BuildConfig.VERSION_CODE))
                .addNetworkInterceptor(StethoWrapper.networkInterceptor())
                .addNetworkInterceptor(LoggingInterceptor())
                .build()
    }

    bind<Downloader>() with singleton {
        object : Downloader {
            val logger = LoggerFactory.getLogger("Picasso.Downloader")
            val fallback = OkHttp3Downloader(instance<OkHttpClient>())
            val cache = instance<com.pr0gramm.app.io.Cache>()

            override fun load(uri: Uri, networkPolicy: Int): Downloader.Response {
                // load thumbnails normally
                if (uri.host.contains("thumb.pr0gramm.com") || uri.path.contains("/thumb.jpg")) {
                    return fallback.load(uri, networkPolicy)
                } else {
                    logger.debug("Using cache to download image {}", uri)
                    cache.get(uri).use { entry ->
                        val fullyCached = entry.fractionCached == 1f
                        return Downloader.Response(entry.inputStreamAt(0), fullyCached, entry.totalSize().toLong())
                    }
                }
            }

            override fun shutdown() {
                fallback.shutdown()
            }
        }
    }

    bind<ProxyService>() with singleton {
        val logger = LoggerFactory.getLogger("ProxyServiceFactory")
        repeat(10) {
            val port = HttpProxyService.randomPort()
            try {
                val proxy = HttpProxyService(instance(), port)
                proxy.start()

                // return the proxy
                return@singleton proxy

            } catch (ioError: IOException) {
                logger.warn("Could not open proxy on port {}: {}", port, ioError.toString())
            }
        }

        logger.warn("Stop trying, using no proxy now.")
        return@singleton object : ProxyService {
            override fun proxy(url: Uri): Uri {
                return url
            }
        }
    }

    bind<com.pr0gramm.app.io.Cache>() with singleton {
        com.pr0gramm.app.io.Cache(app, instance<OkHttpClient>())
    }

    bind<Picasso>() with singleton {
        Picasso.Builder(app)
                .defaultBitmapConfig(Bitmap.Config.RGB_565)
                .memoryCache(GuavaPicassoCache.defaultSizedGuavaCache())
                .downloader(instance<Downloader>())
                .build()
    }

    bind<Api>() with singleton {
        ApiProvider(instance(), instance(), instance(), instance(), instance()).api
    }
}


private class DebugInterceptor : Interceptor {
    private val logger = LoggerFactory.getLogger("DebugInterceptor")

    override fun intercept(chain: Interceptor.Chain): Response {
        checkNotMainThread()

        val request = chain.request()
        if (BuildConfig.DEBUG) {
            logger.warn("Delaying request {} for 100ms", request.url())
            Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS)
        }

        return chain.proceed(request)
    }
}

private class UserAgentInterceptor(val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
                .header(HttpHeaders.USER_AGENT, userAgent)
                .build()

        return chain.proceed(request)
    }
}

private class DoNotCacheInterceptor(vararg domains: String) : Interceptor {
    private val logger = LoggerFactory.getLogger("DoNotCacheInterceptor")
    private val domains: Set<String> = domains.toSet()

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (domains.contains(request.url().host())) {
            logger.info("Disable caching for {}", request.url())
            response.header("Cache-Control", "no-store")
        }

        return response
    }
}

private class LoggingInterceptor : Interceptor {
    val okLogger = LoggerFactory.getLogger("OkHttpClient")

    override fun intercept(chain: Interceptor.Chain): Response {
        val watch = Stopwatch.createStarted()
        val request = chain.request()

        okLogger.info("performing {} http request for {}", request.method(), request.url())
        try {
            val response = chain.proceed(request)
            okLogger.info("{} ({}) took {}", request.url(), response.code(), watch)
            return response

        } catch (error: Exception) {
            okLogger.warn("{} produced error: {}", request.url(), error)
            throw error
        }
    }
}
