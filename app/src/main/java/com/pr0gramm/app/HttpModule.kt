package com.pr0gramm.app

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
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.services.proxy.HttpProxyService
import com.pr0gramm.app.services.proxy.ProxyService
import com.pr0gramm.app.util.AndroidUtility.checkNotMainThread
import com.pr0gramm.app.util.BackgroundScheduler
import com.pr0gramm.app.util.SmallBufferSocketFactory
import com.squareup.picasso.Downloader
import com.squareup.picasso.Picasso
import okhttp3.*
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Scheduler
import rx.lang.kotlin.toObservable
import rx.util.async.Async
import java.io.File
import java.io.IOException
import java.lang.UnsupportedOperationException
import java.util.concurrent.*

/**
 */
fun httpModule(app: ApplicationClass) = Kodein.Module {
    bind<LoginCookieHandler>() with singleton { LoginCookieHandler(instance()) }

    bind<OkHttpClient>() with singleton {
        val executor: ExecutorService = instance()
        val cookieHandler: LoginCookieHandler = instance()

        val cacheDir = File(app.cacheDir, "imgCache")

        OkHttpClient.Builder()
                .cache(okhttp3.Cache(cacheDir, (64 * 1024 * 1024).toLong()))
                .socketFactory(SmallBufferSocketFactory())

                .cookieJar(cookieHandler)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(8, 30, TimeUnit.SECONDS))
                .retryOnConnectionFailure(true)
                .dispatcher(Dispatcher(executor))

                .addNetworkInterceptor(DebugInterceptor())

                .addInterceptor(DoNotCacheInterceptor("vid.pr0gramm.com", "img.pr0gramm.com", "full.pr0gramm.com"))
                .addNetworkInterceptor(UserAgentInterceptor("pr0gramm-app/v" + BuildConfig.VERSION_CODE))
                .addNetworkInterceptor(app.debugNetworkInterceptor())
                .addNetworkInterceptor(LoggingInterceptor())
                .build()
    }

    bind<Downloader>() with singleton {
        object : Downloader {
            val logger = LoggerFactory.getLogger("Picasso.Downloader")
            val fallback = OkHttp3Downloader(instance<OkHttpClient>())
            val cache = instance<Cache>()

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
                val proxy = HttpProxyService(instance<Cache>(), port)
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

    bind<Cache>() with singleton {
        Cache(app, instance<OkHttpClient>())
    }

    bind<Picasso>() with singleton {
        Picasso.Builder(app)
                .defaultBitmapConfig(Bitmap.Config.RGB_565)
                .memoryCache(com.pr0gramm.app.util.GuavaPicassoCache.defaultSizedGuavaCache())
                .downloader(instance<Downloader>())
                .executor(instance<ExecutorService>())
                .build()
    }

    bind<ExecutorService>() with instance(SchedulerExecutorService(BackgroundScheduler.instance()))

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

private class SchedulerExecutorService(val scheduler: Scheduler) : ExecutorService {
    override fun shutdown() {
        // will never shutdown.
    }

    override fun <T : Any?> submit(task: Callable<T>): Future<T> {
        return Async.fromCallable(task, scheduler).toBlocking().toFuture()
    }

    override fun <T : Any?> submit(task: Runnable, result: T): Future<T> {
        return Async.fromRunnable(task, result, scheduler).toBlocking().toFuture()
    }

    override fun submit(task: Runnable): Future<*> {
        return submit(task, null)
    }

    override fun shutdownNow(): MutableList<Runnable> {
        // nope
        return mutableListOf()
    }

    override fun isShutdown(): Boolean {
        return false
    }

    override fun awaitTermination(timeout: Long, unit: TimeUnit?): Boolean {
        while (true) {
            Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS)
        }
    }

    override fun <T : Any?> invokeAny(tasks: MutableCollection<out Callable<T>>): T {
        return invokeAny(tasks, 0L, null)
    }

    override fun <T : Any?> invokeAny(tasks: MutableCollection<out Callable<T>>, timeout: Long, unit: TimeUnit?): T {
        return tasks.toObservable()
                .flatMap { Async.fromCallable(it, scheduler) }
                .switchIfEmpty(Observable.error(object : ExecutionException("No one finished") {}))
                .apply { if (unit != null) timeout(timeout, unit) }
                .toBlocking()
                .first()
    }

    override fun isTerminated(): Boolean {
        return false
    }

    override fun <T : Any?> invokeAll(tasks: MutableCollection<out Callable<T>>): MutableList<Future<T>> {
        return tasks.map { submit(it) }.toMutableList()
    }

    override fun <T : Any?> invokeAll(tasks: MutableCollection<out Callable<T>>, timeout: Long, unit: TimeUnit?): MutableList<Future<T>> {
        throw UnsupportedOperationException()
    }

    override fun execute(command: Runnable?) {
        Async.fromRunnable(command, null, scheduler)
    }
}
