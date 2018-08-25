package com.pr0gramm.app.services

import android.app.Activity
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.support.annotation.DrawableRes
import android.support.v4.content.ContextCompat
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.categories.ExtraCategories
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.orm.Bookmark
import com.pr0gramm.app.services.config.Config
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.util.RxPicasso
import com.pr0gramm.app.util.logger
import com.pr0gramm.app.util.observeOnMainThread
import com.squareup.picasso.Picasso
import rx.Observable
import rx.Observable.combineLatest
import rx.Observable.just
import java.util.*
import java.util.concurrent.TimeUnit

/**
 */
class NavigationProvider(
        private val context: Activity,
        private val userService: UserService,
        private val inboxService: InboxService,
        private val bookmarkService: BookmarkService,
        private val configService: ConfigService,
        private val extraCategories: ExtraCategories,
        private val picasso: Picasso) {

    private val logger = logger("NavigationProvider")

    private val iconBookmark = drawable(R.drawable.ic_black_action_bookmark)
    private val iconFavorites = drawable(R.drawable.ic_black_action_favorite)
    private val iconFeedTypeBestOf = drawable(R.drawable.ic_drawer_bestof)
    private val iconFeedTypeControversial = drawable(R.drawable.ic_category_controversial)
    private val iconFeedTypeNew = drawable(R.drawable.ic_black_action_trending)
    private val iconFeedTypePremium = drawable(R.drawable.ic_black_action_stelz)
    private val iconFeedTypePromoted = drawable(R.drawable.ic_black_action_home)
    private val iconFeedTypeRandom = drawable(R.drawable.ic_action_random)
    private val iconFeedTypeText = drawable(R.drawable.ic_drawer_text_24)
    private val iconInbox = drawable(R.drawable.ic_action_email)
    private val iconSecretSanta = drawable(R.drawable.ic_action_wichteln)
    private val iconUpload = drawable(R.drawable.ic_black_action_upload)

    private val specialMenuItems = configService.observeConfig()
            .observeOnMainThread()
            .distinctUntilChanged()
            .flatMap { resolveSpecial(it) }

    private fun drawable(@DrawableRes id: Int): Drawable {
        return ContextCompat.getDrawable(context, id)!!
    }

    val navigationItems: Observable<List<NavigationItem>>
        get() {
            // observe and merge the menu items from different sources
            fun merge(args: Array<Any>): List<NavigationItem> {
                val result = mutableListOf<NavigationItem>()
                args.filterIsInstance<List<Any?>>().forEach { items ->
                    items.filterIsInstanceTo(result)
                }

                return result
            }

            val rawSources = listOf<Observable<List<NavigationItem>>>(
                    specialMenuItems,

                    categoryNavigationItems(),

                    userService.loginStates
                            .flatMap { bookmarkService.get() }
                            .map { bookmarksToNavItem(it) },

                    inboxService.unreadMessagesCount()
                            .startWith(0)
                            .map { listOf(inboxNavigationItem(it)) },

                    Observable.just(listOf(uploadNavigationItem)))

            val sources = rawSources.map { source ->
                source.startWith(emptyList<NavigationItem>()).retryWhen { err ->
                    logger.warn("Could not get category sub-items: ", err)
                    err.delay(5, TimeUnit.SECONDS)
                }
            }

            return combineLatest(sources, ::merge).distinctUntilChanged()
        }

    /**
     * Adds the default "fixed" items to the menu
     */
    fun categoryNavigationItems(username: String?, extraCategory: Boolean): List<NavigationItem> {

        val items = ArrayList<NavigationItem>()

        items.add(NavigationItem(
                action = ActionType.FILTER,
                title = getString(R.string.action_feed_type_promoted),
                icon = iconFeedTypePromoted,
                filter = FeedFilter().withFeedType(FeedType.PROMOTED)))

        items.add(NavigationItem(
                action = ActionType.FILTER,
                title = getString(R.string.action_feed_type_new),
                icon = iconFeedTypeNew,
                filter = FeedFilter().withFeedType(FeedType.NEW)))

        val settings = Settings.get()
        if (extraCategory) {
            if (settings.showCategoryBestOf) {
                items.add(NavigationItem(
                        action = ActionType.FILTER,
                        title = getString(R.string.action_feed_type_bestof),
                        icon = iconFeedTypeBestOf,
                        filter = FeedFilter().withFeedType(FeedType.BESTOF)))
            }

            if (settings.showCategoryControversial) {

                items.add(NavigationItem(
                        action = ActionType.FILTER,
                        title = getString(R.string.action_feed_type_controversial),
                        icon = iconFeedTypeControversial,
                        filter = FeedFilter().withFeedType(FeedType.CONTROVERSIAL)))
            }

            if (settings.showCategoryRandom) {
                items.add(NavigationItem(
                        action = ActionType.FILTER,
                        title = getString(R.string.action_feed_type_random),
                        icon = iconFeedTypeRandom,
                        filter = FeedFilter().withFeedType(FeedType.RANDOM)))
            }

            if (settings.showCategoryText) {
                items.add(NavigationItem(
                        action = ActionType.FILTER,
                        title = getString(R.string.action_feed_type_text),
                        icon = iconFeedTypeText,
                        filter = FeedFilter().withFeedType(FeedType.TEXT)))
            }
        }

        if (settings.showCategoryPremium) {
            if (userService.isPremiumUser) {

                items.add(NavigationItem(
                        action = ActionType.FILTER,
                        title = getString(R.string.action_feed_type_premium),
                        icon = iconFeedTypePremium,
                        filter = FeedFilter().withFeedType(FeedType.PREMIUM)))
            }
        }

        if (username != null) {
            if (configService.config().secretSanta) {
                items.add(NavigationItem(
                        action = ActionType.URI,
                        title = getString(R.string.action_secret_santa),
                        icon = iconSecretSanta,
                        uri = Uri.parse("https://pr0gramm.com/secret-santa/iap?iap=true")))
            }

            items.add(NavigationItem(
                    action = ActionType.FAVORITES,
                    title = getString(R.string.action_favorites),
                    icon = iconFavorites,
                    filter = FeedFilter().withFeedType(FeedType.NEW).withLikes(username)))
        }

        return items
    }

    /**
     * Returns the menu item that takes the user to the inbox.
     */
    private fun inboxNavigationItem(unreadCount: Int): NavigationItem {
        return NavigationItem(
                action = ActionType.MESSAGES,
                title = getString(R.string.action_inbox),
                icon = iconInbox,
                layout = R.layout.left_drawer_nav_item_inbox,
                unreadCount = unreadCount)
    }

    private fun bookmarksToNavItem(entries: List<Bookmark>): List<NavigationItem> {
        val premium = userService.isPremiumUser
        return entries
                .filter { premium || it.asFeedFilter().feedType !== FeedType.PREMIUM }
                .map { entry ->
                    val icon = iconBookmark.constantState.newDrawable()
                    val title = entry.title.toUpperCase()

                    NavigationItem(
                            action = ActionType.BOOKMARK,
                            title = title, icon = icon, bookmark = entry,
                            filter = entry.asFeedFilter())
                }
    }

    private fun categoryNavigationItems(): Observable<List<NavigationItem>> {
        val username = userService.loginStates.map { it.name }
        val categoriesAvailable = extraCategories.categoriesAvailable

        return combineLatest(username, categoriesAvailable, this::categoryNavigationItems)
                .startWith(emptyList<NavigationItem>())
    }

    /**
     * Returns the menu item that takes the user to the upload activity.
     */
    private val uploadNavigationItem: NavigationItem = NavigationItem(
            action = ActionType.UPLOAD,
            title = getString(R.string.action_upload),
            icon = iconUpload)

    /**
     * Short for context.getString(...)
     */
    private fun getString(id: Int): String {
        return context.getString(id)
    }

    /**
     * Resolves the special for the given config and loads the icon,
     * if there is one.
     */
    private fun resolveSpecial(config: Config): Observable<List<NavigationItem>> {
        val item = config.specialMenuItem ?: return just(emptyList())

        logger.info("Loading item {}", item)

        return Observable.just(item)
                .flatMap {
                    RxPicasso.load(picasso, picasso.load(Uri.parse(item.icon))
                            .noPlaceholder()
                            .resize(iconUpload.intrinsicWidth, iconUpload.intrinsicHeight))
                }
                .map { bitmap ->
                    logger.info("Loaded image for {}", item)
                    val icon = BitmapDrawable(context.resources, bitmap)
                    val uri = Uri.parse(item.link)

                    listOf(NavigationItem(ActionType.URI, item.name, icon,
                            uri = uri,
                            layout = R.layout.left_drawer_nav_item_special))
                }
                .retryWhen { err ->
                    err.zipWith(Observable.range(1, 3)) { n, i -> i }.flatMap { idx ->
                        logger.debug("Delay retry by {} second(s)", idx)
                        Observable.timer(idx.toLong(), TimeUnit.SECONDS)
                    }
                }
    }

    class NavigationItem(val action: ActionType,
                         val title: String,
                         val icon: Drawable,
                         val layout: Int = R.layout.left_drawer_nav_item,
                         val filter: FeedFilter? = null,
                         val bookmark: Bookmark? = null,
                         val unreadCount: Int = 0,
                         val uri: Uri? = null) {

        private val hashCode by lazy { listOf(action, title, layout, filter, bookmark, unreadCount, uri).hashCode() }

        val hasFilter: Boolean get() = filter != null

        override fun hashCode(): Int = hashCode

        override fun equals(other: Any?): Boolean {
            return other is NavigationItem
                    && other.action == action
                    && other.title == title
                    && other.layout == layout
                    && other.filter == filter
                    && other.bookmark == bookmark
                    && other.unreadCount == unreadCount
                    && other.uri == uri
        }
    }

    enum class ActionType {
        FILTER, BOOKMARK, MESSAGES, UPLOAD, FAVORITES, URI
    }
}
