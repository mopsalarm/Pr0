package com.pr0gramm.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.os.Build
import com.google.firebase.analytics.FirebaseAnalytics
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.ApiProvider
import com.pr0gramm.app.api.pr0gramm.LoginCookieJar
import com.pr0gramm.app.feed.FeedService
import com.pr0gramm.app.feed.FeedServiceImpl
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.services.*
import com.pr0gramm.app.services.Track.context
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.services.preloading.PreloadManager
import com.pr0gramm.app.sync.SyncService
import com.pr0gramm.app.ui.AdService
import com.pr0gramm.app.ui.FancyExifThumbnailGenerator
import com.pr0gramm.app.ui.TagSuggestionService
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.Module
import com.squareup.picasso.Downloader
import com.squareup.picasso.Picasso
import com.squareup.sqlbrite.BriteDatabase
import com.squareup.sqlbrite.SqlBrite
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
import rx.schedulers.Schedulers
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

fun appInjector(app: Application) = Module.build {
    bind<Application>() with instance(app)

    bind<SharedPreferences>() with singleton {
        app.getSharedPreferences("pr0gramm", Context.MODE_PRIVATE)
    }

    bind<Settings>() with singleton {
        Settings.get()
    }

    bind<SQLiteOpenHelper>() with singleton {
        Databases.PlainOpenHelper(app)
    }

    bind<Holder<SQLiteDatabase>>() with singleton {
        Holder {
            val helper: SQLiteOpenHelper = instance()
            helper.writableDatabase
        }
    }

    bind<BriteDatabase>() with singleton {
        val logger = Logger("SqlBrite")
        SqlBrite.Builder()
                .logger { logger.info { it } }
                .build()
                .wrapDatabaseHelper(instance<SQLiteOpenHelper>(), Schedulers.computation())
    }

    bind<FirebaseAnalytics>() with instance(FirebaseAnalytics.getInstance(app).apply {
        setAnalyticsCollectionEnabled(true)
    })

    bind<LoginCookieJar>() with singleton { LoginCookieJar(app, instance()) }

    bind<OkHttpClient>() with eagerSingleton {
        val cookieJar: LoginCookieJar = instance()

        val cacheDir = File(app.cacheDir, "imgCache")

        okHttpClientBuilder(app)
                .cache(Cache(cacheDir, (64 * 1024 * 1024).toLong()))
                .socketFactory(SmallBufferSocketFactory())

                .cookieJar(cookieJar)
                .connectionPool(ConnectionPool(8, 30, TimeUnit.SECONDS))
                .dns(CustomDNS(app))

                .apply {
                    debug {
                        @Suppress("ConstantConditionIf")
                        if (Debug.debugInterceptor) {
                            addInterceptor(DebugInterceptor())
                        }
                    }
                }

                .addNetworkInterceptor(DoNotCacheInterceptor("vid.pr0gramm.com", "img.pr0gramm.com", "full.pr0gramm.com"))
                .addNetworkInterceptor(UserAgentInterceptor("pr0gramm-app/v${BuildConfig.VERSION_CODE} android${Build.VERSION.SDK_INT}"))
                .addNetworkInterceptor(UpdateServerTimeInterceptor())
                .build()
    }

    bind<Downloader>() with singleton {
        CachingDownloader(cache = instance(), httpClient = instance())
    }

    bind<com.pr0gramm.app.io.Cache>() with singleton {
        com.pr0gramm.app.io.Cache(app, httpClient = instance())
    }

    bind<Picasso>() with singleton {
        Picasso.Builder(app)
                .defaultBitmapConfig(Bitmap.Config.ARGB_8888)
                .memoryCache(GuavaPicassoCache.defaultSizedGuavaCache())
                .downloader(instance<Downloader>())
                .build()
    }

    bind<Api>() with singleton {
        ApiProvider("https://pr0gramm.com/", instance(), instance()).api
    }

    val seenService = SeenService(app)
    val inMemoryCacheService = InMemoryCacheService()

    bind<SeenService>() with instance(seenService)
    bind<InMemoryCacheService>() with instance(inMemoryCacheService)

    bind<FancyExifThumbnailGenerator>() with singleton { FancyExifThumbnailGenerator(app, instance()) }

    bind<ConfigService>() with singleton { ConfigService(app, instance(), instance()) }
    bind<BookmarkSyncService>() with singleton { BookmarkSyncService(instance(), instance()) }
    bind<BookmarkService>() with eagerSingleton { BookmarkService(instance(), instance(), instance()) }
    bind<InboxService>() with singleton { InboxService(instance(), instance()) }

    bind<UserService>() with eagerSingleton { UserService(instance(), instance(), instance(), instance(), instance(), instance(), instance(), instance()) }
    bind<VoteService>() with singleton { VoteService(instance(), instance(), instance()) }
    bind<SingleShotService>() with singleton { SingleShotService(instance()) }
    bind<PreloadManager>() with eagerSingleton { PreloadManager(instance()) }
    bind<FavedCommentService>() with singleton { FavedCommentService(instance(), instance()) }
    bind<RecentSearchesServices>() with singleton { RecentSearchesServices(instance()) }

    bind<AdminService>() with singleton { AdminService(instance(), instance()) }
    bind<AdService>() with singleton { AdService(instance(), instance()) }
    bind<ContactService>() with singleton { ContactService(instance()) }
    bind<DownloadService>() with singleton { DownloadService(instance(), instance(), instance()) }
    bind<FeedbackService>() with singleton { FeedbackService(instance()) }
    bind<FeedService>() with singleton { FeedServiceImpl(instance()) }
    bind<GifDrawableLoader>() with singleton { GifDrawableLoader(app.cacheDir, instance()) }
    bind<InfoMessageService>() with singleton { InfoMessageService(instance()) }
    bind<InviteService>() with singleton { InviteService(instance()) }
    bind<StatisticsService>() with singleton { StatisticsService(instance()) }
    bind<TagSuggestionService>() with eagerSingleton { TagSuggestionService(instance()) }
    bind<UserClassesService>() with singleton { UserClassesService(instance<ConfigService>()) }
    bind<BenisRecordService>() with singleton { BenisRecordService(instance()) }
    bind<ShareService>() with singleton { ShareService(instance()) }

    bind<KVService>() with singleton { KVService(instance()) }

    bind<SyncService>() with eagerSingleton {
        SyncService(instance(), instance(), instance(), instance(), instance())
    }

    bind<SettingsTrackerService>() with singleton { SettingsTrackerService(instance()) }

    bind<NotificationService>() with singleton { NotificationService(instance(), instance(), instance()) }

    bind<RulesService>() with singleton { RulesService(instance()) }
    bind<StalkService>() with singleton { StalkService(instance()) }

    bind<UploadService>() with singleton {
        UploadService(instance(), instance(), instance(), instance(), instance(), instance())
    }

    bind<UserSuggestionService>() with singleton { UserSuggestionService(instance()) }

    bind<Config>() with provider { instance<ConfigService>().config() }
}


const val TagApiURL = "api.baseurl"

fun okHttpClientBuilder(app: Application): OkHttpClient.Builder {
    val connectionSpecs = ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS).run {
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

    return OkHttpClient.Builder()
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionSpecs(listOf(connectionSpecs, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
            .configureSSLSocketFactoryAndSecurity(app)
            .addNetworkInterceptor(LoggingInterceptor())
}

private class UpdateServerTimeInterceptor : Interceptor {
    private val format by threadLocal {
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ROOT)
        format.isLenient = false
        format
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val requestStartTime = System.currentTimeMillis()
        val response = chain.proceed(request)

        // we need the time of the network request
        val requestTime = System.currentTimeMillis() - requestStartTime

        if (response.isSuccessful && request.url.host == "pr0gramm.com") {
            response.header("Date")?.let { dateValue ->
                val serverTime = try {
                    format.parse(dateValue)
                } catch (err: Throwable) {
                    null
                }

                if (serverTime != null) {
                    val serverTimeApprox = serverTime.time + requestTime / 2
                    TimeFactory.updateServerTime(Instant(serverTimeApprox))
                }
            }
        }

        return response
    }

}

private class DebugInterceptor : Interceptor {
    private val logger = Logger("DebugInterceptor")

    override fun intercept(chain: Interceptor.Chain): Response {
        checkNotMainThread()

        val request = chain.request()

        val watch = Stopwatch()
        try {
            if ("pr0gramm.com" in request.url.toString()) {
                TimeUnit.MILLISECONDS.sleep(750)
            } else {
                TimeUnit.MILLISECONDS.sleep(500)
            }

            val response = chain.proceed(request)
            logger.debug { "Delayed request to ${request.url} took $watch (status=${response.code})" }
            return response
        } catch (err: Throwable) {
            logger.debug { "Delayed request to ${request.url} took $watch, error $err" }
            throw err
        }
    }
}

private class UserAgentInterceptor(val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
                .header("User-Agent", userAgent)
                .build()

        return chain.proceed(request)
    }
}

private class DoNotCacheInterceptor(vararg domains: String) : Interceptor {
    private val logger = Logger("DoNotCacheInterceptor")
    private val domains: Set<String> = domains.toSet()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (domains.contains(request.url.host)) {
            logger.debug { "Disable caching for ${request.url}" }
            response.header("Cache-Control", "no-store")
        }

        return response
    }
}

private class LoggingInterceptor : Interceptor {
    val okLogger = Logger("OkHttpClient")

    override fun intercept(chain: Interceptor.Chain): Response {
        val watch = Stopwatch()
        val request = chain.request()

        okLogger.info { "performing ${request.method} http request for ${request.url}" }
        try {
            val response = chain.proceed(request)
            okLogger.info { "${request.url} (${response.code}) took $watch" }
            return response

        } catch (error: Exception) {
            okLogger.warn { "${request.url} produced error: $error" }
            throw error
        }
    }
}

private class CustomDNS(app: Application) : Dns {
    val logger = Logger("CustomDNS")

    private val okHttpClient by lazy {
        okHttpClientBuilder(app)
                .cache(Cache(context.cacheDir.resolve("dns-cache"), 1024 * 1024))
                .build()
    }

    private val dnsOverHttps by lazy {
        DnsOverHttps.Builder()
                .client(okHttpClient)
                .post(false)
                .resolvePrivateAddresses(false)
                .resolvePublicAddresses(true)
                .includeIPv6(false)
                .url("https://1.1.1.1/dns-query".toHttpUrl())
                .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname == "127.0.0.1" || hostname == "localhost") {
            return listOf(InetAddress.getByName("127.0.0.1"))
        }

        // short circuit for inet4 addresses
        if (hostname.matches("""^[0-9]+[.][0-9]+[.][0-9]+[.][0-9]+$""".toRegex())) {
            return listOf(Inet4Address.getByName(hostname))
        }

        val resolvers = mutableListOf<NamedResolver>()

        if (Settings.get().useDoH) {
            resolvers += NamedResolver("doh-okhttp", dnsOverHttps)
        }

        resolvers += NamedResolver("system", Dns.SYSTEM)

        for ((name, resolver) in resolvers) {
            try {
                logger.debug { "Try resolver $name" }
                val addresses = resolver.lookup(hostname)
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

                if (addresses.isNotEmpty()) {
                    Stats().increment("dns.okay", "resolver:$name")

                    logger.debug { "Resolver $name for $hostname returned $addresses" }
                    return addresses
                }
            } catch (err: UnknownHostException) {
                logger.warn { "Resolver $name failed with UnknownHostException" }

            } catch (err: Exception) {
                logger.warn(err) { "Resolver $name failed" }
            }
        }

        return listOf()
    }

    private data class NamedResolver(val name: String, val resolver: Dns)
}
