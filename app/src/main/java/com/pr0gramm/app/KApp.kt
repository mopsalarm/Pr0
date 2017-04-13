package com.pr0gramm.app

import android.content.SharedPreferences
import com.github.salomonbrys.kodein.*
import com.github.salomonbrys.kodein.android.autoAndroidModule
import com.pr0gramm.app.services.GifDrawableLoader
import com.pr0gramm.app.services.InMemoryCacheService
import com.pr0gramm.app.services.UserSuggestionService
import com.pr0gramm.app.services.gif.GifToVideoService
import com.pr0gramm.app.services.proxy.ProxyService
import com.pr0gramm.app.ui.FancyExifThumbnailGenerator
import com.squareup.picasso.Downloader
import com.squareup.picasso.Picasso
import javax.inject.Inject

internal class KApp(private val app: ApplicationClass) : KodeinAware {

    @Inject lateinit var inMemoryCacheService: InMemoryCacheService
    @Inject lateinit var fancyExifThumbnailGenerator: FancyExifThumbnailGenerator
    @Inject lateinit var proxyService: ProxyService
    @Inject lateinit var downloader: Downloader
    @Inject lateinit var gifDrawableLoader: GifDrawableLoader
    @Inject lateinit var gifToVideoService: GifToVideoService
    @Inject lateinit var UserSuggestionService: UserSuggestionService

    init {
        app.appComponent.get().inject(this)
    }

    override val kodein: Kodein by Kodein.lazy {
        import(autoAndroidModule(app))

        bind<SharedPreferences>(overrides = true) with instance(app.appComponent.get().sharedPreferences())

        bind<Settings>() with instance(Settings.get())

        bind<Picasso>() with instance(app.appComponent.get().picasso())
        bind<InMemoryCacheService>() with instance(inMemoryCacheService)
        bind<FancyExifThumbnailGenerator>() with instance(fancyExifThumbnailGenerator)

        bind<ProxyService>() with instance(proxyService)

        bind<Downloader>() with instance(downloader)
        bind<GifDrawableLoader>() with instance(gifDrawableLoader)
        bind<GifToVideoService>() with instance(gifToVideoService)

        bind<UserSuggestionService>() with instance(UserSuggestionService)

    }
}
