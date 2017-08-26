package com.pr0gramm.app

import android.app.Application
import com.github.salomonbrys.kodein.*
import com.github.salomonbrys.kodein.android.androidActivityScope
import com.google.gson.Gson
import com.pr0gramm.app.api.categories.ExtraCategories
import com.pr0gramm.app.feed.FeedService
import com.pr0gramm.app.feed.FeedServiceImpl
import com.pr0gramm.app.services.*
import com.pr0gramm.app.services.config.Config
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.services.gif.GifToVideoService
import com.pr0gramm.app.services.gif.MyGifToVideoService
import com.pr0gramm.app.services.preloading.DatabasePreloadManager
import com.pr0gramm.app.services.preloading.PreloadManager
import com.pr0gramm.app.ui.AdService
import com.pr0gramm.app.ui.FancyExifThumbnailGenerator

fun servicesModule(app: Application) = Kodein.Module {
    bind<Gson>() with instance(GsonModule.INSTANCE)
    bind<SeenService>() with instance(SeenService(app))
    bind<InMemoryCacheService>() with instance(InMemoryCacheService())

    bind<FancyExifThumbnailGenerator>() with singleton { FancyExifThumbnailGenerator(app, instance()) }

    bind<ExtraCategories>() with singleton { ExtraCategories(instance(), instance(), instance()) }
    bind<ConfigService>() with singleton { ConfigService(app, instance(), instance(), instance()) }
    bind<BookmarkService>() with singleton { BookmarkService(instance()) }
    bind<InboxService>() with singleton { InboxService(instance(), instance()) }

    bind<UserService>() with singleton { UserService(instance(), instance(), instance(), instance(), instance(), instance(), instance(), instance()) }
    bind<VoteService>() with singleton { VoteService(instance(), instance(), instance()) }
    bind<SingleShotService>() with singleton { SingleShotService(instance()) }
    bind<PreloadManager>() with singleton { DatabasePreloadManager(instance()) }
    bind<FavedCommentService>() with singleton { FavedCommentService(instance(), instance()) }
    bind<RecentSearchesServices>() with singleton { RecentSearchesServices(instance(), instance()) }

    bind<AdminService>() with singleton { AdminService(instance(), instance()) }
    bind<AdService>() with singleton { AdService(instance(), instance()) }
    bind<ContactService>() with singleton { ContactService(instance()) }
    bind<DownloadService>() with singleton { DownloadService(instance(), instance(), instance(), instance()) }
    bind<FeedbackService>() with singleton { FeedbackService(instance()) }
    bind<FeedService>() with singleton { FeedServiceImpl(instance(), instance(), instance()) }
    bind<GifDrawableLoader>() with singleton { GifDrawableLoader(instance("cache"), instance()) }
    bind<GifToVideoService>() with singleton { MyGifToVideoService(instance()) }
    bind<InfoMessageService>() with singleton { InfoMessageService(instance()) }
    bind<InviteService>() with singleton { InviteService(instance()) }

    bind<BackupService>() with singleton {
        BackupService(instance<SeenService>(), instance<Settings>(), instance<UserService>(), instance<BookmarkService>())
    }

    bind<SettingsTrackerService>() with singleton { SettingsTrackerService(instance(), instance()) }

    bind<NotificationService>() with singleton { NotificationService(instance(), instance(), instance(), instance()) }

    bind<RulesService>() with singleton { RulesService(instance()) }
    bind<StalkService>() with singleton { StalkService(instance()) }

    bind<UploadService>() with singleton { UploadService(instance(), instance(), instance(), instance(), instance()) }
    bind<UserSuggestionService>() with singleton { UserSuggestionService(instance()) }

    bind<Config>() with provider { instance<ConfigService>().config() }

    bind<NavigationProvider>() with scopedSingleton(androidActivityScope) { activity ->
        NavigationProvider(activity, instance(), instance(), instance(), instance(), instance())
    }
}
