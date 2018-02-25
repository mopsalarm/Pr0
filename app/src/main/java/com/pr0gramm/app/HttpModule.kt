package com.pr0gramm.app

import android.graphics.Bitmap
import android.net.Uri
import android.support.v4.util.LruCache
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
import com.pr0gramm.app.util.GuavaPicassoCache
import com.pr0gramm.app.util.SmallBufferSocketFactory
import com.pr0gramm.app.util.debug
import com.squareup.picasso.Downloader
import com.squareup.picasso.NetworkPolicy.shouldReadFromDiskCache
import com.squareup.picasso.NetworkPolicy.shouldWriteToDiskCache
import com.squareup.picasso.Picasso
import okhttp3.*
import org.slf4j.LoggerFactory
import org.xbill.DNS.*
import rx.Observable
import rx.Scheduler
import rx.lang.kotlin.toObservable
import rx.util.async.Async
import java.io.File
import java.io.IOException
import java.lang.UnsupportedOperationException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.*
import kotlin.concurrent.timer

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
                .dns(FallbackDns())

                .addNetworkInterceptor(DebugInterceptor())

                .addInterceptor(DoNotCacheInterceptor("vid.pr0gramm.com", "img.pr0gramm.com", "full.pr0gramm.com"))
                .addNetworkInterceptor(UserAgentInterceptor("pr0gramm-app/v" + BuildConfig.VERSION_CODE))
                .addNetworkInterceptor(app.debugNetworkInterceptor())
                .addNetworkInterceptor(LoggingInterceptor())
                .build()
    }

    bind<Downloader>() with singleton {
        val fallback = OkHttp3Downloader(instance<OkHttpClient>())
        val cache = instance<Cache>()
        PicassoDownloader(cache, fallback)
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
                .memoryCache(GuavaPicassoCache.defaultSizedGuavaCache())
                .downloader(instance<Downloader>())
                .build()
    }

    bind<ExecutorService>() with instance(SchedulerExecutorService(BackgroundScheduler.instance()))

    bind<Api>() with singleton {
        ApiProvider(instance(), instance(), instance(), instance(), instance()).api
    }
}


private class PicassoDownloader(val cache: Cache, val fallback: OkHttp3Downloader) : Downloader {
    val logger = LoggerFactory.getLogger("Picasso.Downloader")

    private val memoryCache = object : LruCache<String, ByteArray>(1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    init {
        debug {
            timer(period = 10000) {
                logger.info("Cache stats: {}", memoryCache.toString())
            }
        }
    }

    override fun load(uri: Uri, networkPolicy: Int): Downloader.Response {
        // load thumbnails normally
        if (uri.host.contains("thumb.pr0gramm.com") || uri.path.contains("/thumb.jpg")) {
            // try memory cache first.
            if (shouldReadFromDiskCache(networkPolicy)) {
                memoryCache.get(uri.toString())?.let {
                    return Downloader.Response(it.inputStream(), true, it.size.toLong())
                }
            }

            // do request using fallback - network or disk.
            val response = fallback.load(uri, networkPolicy)

            // check if we want to cache the response in memory
            if (shouldWriteToDiskCache(networkPolicy) && response.contentLength in (1 until 20 * 1024)) {
                // directly read the response and buffer it in-memory
                val bytes = response.inputStream.use {
                    it.readBytes(estimatedSize = response.contentLength.toInt().coerceAtLeast(1024))
                }

                memoryCache.put(uri.toString(), bytes)
                return Downloader.Response(bytes.inputStream(), true, bytes.size.toLong())
            } else {
                return response
            }
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


private class DebugInterceptor : Interceptor {
    private val logger = LoggerFactory.getLogger("DebugInterceptor")

    override fun intercept(chain: Interceptor.Chain): Response {
        checkNotMainThread()

        val request = chain.request()
        debug {
            logger.warn("Delaying request {} for a short time", request.url())
            TimeUnit.MILLISECONDS.sleep(750)
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
            logger.debug("Disable caching for {}", request.url())
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

private class FallbackDns : Dns {
    val logger = LoggerFactory.getLogger("FallbackDns");

    val resolver = SimpleResolver("8.8.8.8")
    val cache = org.xbill.DNS.Cache()

    override fun lookup(hostname: String): MutableList<InetAddress> {
        val resolved = try {
            Dns.SYSTEM.lookup(hostname)
                    .filterNot { it.isAnyLocalAddress }
                    .filterNot { it.isLinkLocalAddress }
                    .filterNot { it.isLoopbackAddress }
                    .filterNot { it.isMulticastAddress }
                    .filterNot { it.isSiteLocalAddress }
                    .filterNot { it.isMCSiteLocal }
                    .filterNot { it.isMCGlobal }
                    .filterNot { it.isMCLinkLocal }
                    .filterNot { it.isMCNodeLocal }
                    .filterNot { it.isMCOrgLocal }
                    .toMutableList()

        } catch (err: UnknownHostException) {
            mutableListOf<InetAddress>()
        }

        if (resolved.isNotEmpty()) {
            debug {
                logger.info("System resolver for {} returned {}", hostname, resolved)
            }

            return resolved
        } else {
            val fallback = fallbackLookup(hostname)
            debug {
                logger.info("Fallback resolver for {} returned {}", hostname, fallback)
            }
            return fallback
        }
    }

    private fun fallbackLookup(hostname: String): MutableList<InetAddress> {
        val lookup = Lookup(hostname, Type.A, DClass.IN)
        lookup.setResolver(resolver)
        lookup.setCache(cache)

        val records = lookup.run()
        return records.filterIsInstance<ARecord>().mapTo(mutableListOf()) { it.address }
    }
}