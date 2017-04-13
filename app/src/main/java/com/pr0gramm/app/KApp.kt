package com.pr0gramm.app

import android.content.SharedPreferences
import com.github.salomonbrys.kodein.*
import com.github.salomonbrys.kodein.android.autoAndroidModule
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.services.*
import com.pr0gramm.app.services.gif.GifToVideoService
import com.pr0gramm.app.services.gif.MyGifToVideoService
import com.pr0gramm.app.services.proxy.ProxyService
import com.pr0gramm.app.ui.FancyExifThumbnailGenerator
import com.squareup.picasso.Downloader
import com.squareup.picasso.Picasso
import okhttp3.OkHttpClient
import javax.inject.Inject

internal class KApp(private val app: ApplicationClass) : KodeinAware {

    @Inject lateinit var inMemoryCacheService: InMemoryCacheService
    @Inject lateinit var fancyExifThumbnailGenerator: FancyExifThumbnailGenerator
    @Inject lateinit var proxyService: ProxyService
    @Inject lateinit var downloader: Downloader
    @Inject lateinit var UserSuggestionService: UserSuggestionService
    @Inject lateinit var Api: Api
    @Inject lateinit var OkHttpClient: OkHttpClient
    @Inject lateinit var Cache: Cache
    @Inject lateinit var UserService: UserService

    init {
        app.appComponent.get().inject(this)
    }

    override val kodein: Kodein by Kodein.lazy {
        import(autoAndroidModule(app))

        bind<SharedPreferences>(overrides = true) with instance(app.appComponent.get().sharedPreferences())

        bind<Settings>() with instance(Settings.get())
        bind<OkHttpClient>() with instance(OkHttpClient)
        bind<Api>() with instance(Api)

        bind<Picasso>() with instance(app.appComponent.get().picasso())
        bind<InMemoryCacheService>() with instance(inMemoryCacheService)
        bind<FancyExifThumbnailGenerator>() with instance(fancyExifThumbnailGenerator)

        bind<Cache>() with instance(Cache)
        bind<ProxyService>() with instance(proxyService)

        bind<Downloader>() with instance(downloader)

        bind<UserService>() with instance(UserService)
        bind<UserSuggestionService>() with instance(UserSuggestionService)

        bind<ContactService>() with singleton { ContactService(instance()) }
        bind<FeedbackService>() with singleton { FeedbackService(instance()) }
        bind<GifDrawableLoader>() with singleton { GifDrawableLoader(app, instance()) }
        bind<GifToVideoService>() with singleton { MyGifToVideoService(instance()) }

    }
}
