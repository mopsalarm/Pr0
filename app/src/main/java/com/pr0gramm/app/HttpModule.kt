package com.pr0gramm.app

import android.graphics.Bitmap
import android.net.Uri
import android.support.v4.util.LruCache
import com.google.common.base.Stopwatch
import com.google.common.base.Throwables
import com.google.common.net.HttpHeaders
import com.google.common.util.concurrent.Uninterruptibles
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
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import okhttp3.*
import org.kodein.di.Kodein
import org.kodein.di.erased.bind
import org.kodein.di.erased.eagerSingleton
import org.kodein.di.erased.instance
import org.kodein.di.erased.singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xbill.DNS.*
import rx.Observable
import rx.Scheduler
import rx.Single
import rx.lang.kotlin.toObservable
import rx.schedulers.Schedulers
import rx.util.async.Async
import java.io.File
import java.io.IOException
import java.lang.UnsupportedOperationException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.*
import kotlin.concurrent.timer


const val TagApiURL = "api.baseurl"

/**
 */
fun httpModule(app: ApplicationClass) = Kodein.Module("http") {
    bind<LoginCookieHandler>() with singleton { LoginCookieHandler(app, instance()) }

    bind<Dns>() with singleton { FallbackDns() }

    bind<String>(TagApiURL) with instance("https://pr0gramm.com/")

    bind<OkHttpClient>() with singleton {
        val executor: ExecutorService = instance()
        val cookieHandler: LoginCookieHandler = instance()

        val cacheDir = File(app.cacheDir, "imgCache")

        val spec = ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS).run {
            tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)

            cipherSuites(
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,

                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_3DES_EDE_CBC_SHA,

                    // and more from https://github.com/square/okhttp/issues/3894
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA)
            build()
        }

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
                .dns(instance())
                .connectionSpecs(listOf(spec, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))

                .addInterceptor(DebugInterceptor())

                .addInterceptor(DoNotCacheInterceptor("vid.pr0gramm.com", "img.pr0gramm.com", "full.pr0gramm.com"))
                .addNetworkInterceptor(UserAgentInterceptor("pr0gramm-app/v" + BuildConfig.VERSION_CODE))
                .addNetworkInterceptor(LoggingInterceptor())
                .build()
    }

    bind<Downloader>() with singleton {
        val fallback = OkHttp3Downloader(instance<OkHttpClient>())
        val cache = instance<Cache>()
        PicassoDownloader(cache, fallback)
    }

    bind<Single<ProxyService>>(tag = "proxyServiceSingle") with eagerSingleton {
        Async.start({
            checkNotMainThread()

            val logger = LoggerFactory.getLogger("ProxyServiceFactory")
            repeat(10) {
                val port = HttpProxyService.randomPort()
                logger.debug("Trying port {}", port)

                try {
                    val proxy = HttpProxyService(instance<Cache>(), port)
                    proxy.start()

                    // return the proxy
                    return@start proxy

                } catch (ioError: IOException) {
                    logger.warn("Could not open proxy on port {}: {}", port, ioError.toString())
                }
            }

            logger.warn("Stop trying, using no proxy now.")
            return@start object : ProxyService {
                override fun proxy(url: Uri): Uri {
                    return url
                }
            }
        }, Schedulers.io()).toSingle()
    }

    bind<ProxyService>() with singleton {
        instance<Single<ProxyService>>(tag = "proxyServiceSingle").toBlocking().value()
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
        val base = instance<String>(TagApiURL)
        ApiProvider(base, instance(), instance(), instance()).api
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

    override fun load(request: Request): Response {
        // load thumbnails normally
        val url = request.url()
        if (url.host().contains("thumb.pr0gramm.com") || url.encodedPath().contains("/thumb.jpg")) {
            // try memory cache first.
            memoryCache.get(url.toString())?.let {
                return Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_0)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(MediaType.parse("image/jpeg"), it))
                        .build()
            }

            // do request using fallback - network or disk.
            val response = fallback.load(request)

            // check if we want to cache the response in memory
            response.body()?.let { body ->
                if (body.contentLength() in (1 until 20 * 1024)) {
                    val bytes = body.bytes()

                    memoryCache.put(url.toString(), bytes)
                    return response.newBuilder()
                            .body(ResponseBody.create(body.contentType(), bytes))
                            .build()
                }
            }

            return response
        } else {
            logger.debug("Using cache to download image {}", url)
            cache.get(Uri.parse(url.toString())).use { entry ->
                return entry.toResponse(request)
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
                .switchIfEmpty(Observable.error(ExecutionException(IllegalStateException("No one finished"))))
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
    val logger: Logger = LoggerFactory.getLogger("FallbackDns")

    val resolver = SimpleResolver("8.8.8.8")
    val cache = org.xbill.DNS.Cache()

    override fun lookup(hostname: String): MutableList<InetAddress> {
        if (hostname == "127.0.0.1" || hostname == "localhost") {
            return mutableListOf(InetAddress.getByName("127.0.0.1"))
        }

        val resolved = try {
            Dns.SYSTEM.lookup(hostname)
        } catch (err: UnknownHostException) {
            emptyList<InetAddress>()
        }

        val resolvedFiltered = resolved
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

        if (resolvedFiltered.isNotEmpty()) {
            debug {
                logger.info("System resolver for {} returned {}", hostname, resolved)
            }

            return resolved.toMutableList()
        } else {
            val fallback = try {
                fallbackLookup(hostname)
            } catch (r: Throwable) {
                if (Throwables.getCausalChain(r).any { it is Error }) {
                    // sometimes the Lookup class does not initialize correctly, in that case, we'll
                    // just return an empty array here and delegate back to the systems resolver.
                    arrayListOf<InetAddress>()
                } else {
                    throw r
                }
            }

            debug {
                logger.info("Fallback resolver for {} returned {}", hostname, fallback)
            }

            if (fallback.isNotEmpty()) {
                return fallback
            }

            // still nothing? lets just return whatever the system told us
            return resolved.toMutableList()
        }
    }

    private fun fallbackLookup(hostname: String): MutableList<InetAddress> {
        val lookup = Lookup(hostname, Type.A, DClass.IN)
        lookup.setResolver(resolver)
        lookup.setCache(cache)

        val records: Array<Record> = lookup.run() ?: return mutableListOf()
        return records.filterIsInstance<ARecord>().mapTo(mutableListOf()) { it.address }
    }
}