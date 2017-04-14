package com.pr0gramm.app.services

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.support.annotation.DrawableRes
import android.support.v4.content.ContextCompat
import com.google.common.collect.ImmutableList
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.categories.ExtraCategories
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.orm.Bookmark
import com.pr0gramm.app.services.config.ConfigService
import rx.Observable
import rx.Observable.combineLatest
import rx.Observable.empty
import java.util.*

/**
 */
class NavigationProvider(
        activity: Activity,
        private val userService: UserService,
        private val inboxService: InboxService,
        private val bookmarkService: BookmarkService,
        private val configService: ConfigService,
        private val extraCategories: ExtraCategories) {

    private val context: Context = activity.applicationContext

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

    private fun drawable(@DrawableRes id: Int): Drawable {
        return ContextCompat.getDrawable(context, id)
    }

    fun navigationItems(): Observable<List<NavigationItem>> {
        // observe and merge the menu items from different sources
        return combineLatest(ImmutableList.of(
                categoryNavigationItems(),

                userService.loginState()
                        .flatMap { bookmarkService.get() }
                        .startWith(emptyList<Bookmark>())
                        .map({ bookmarksToNavItem(it) })
                        .onErrorResumeNext(empty()),

                inboxService.unreadMessagesCount()
                        .startWith(0)
                        .map({ listOf(inboxNavigationItem(it)) }),

                Observable.just(listOf(uploadNavigationItem))
        )) { args ->
            val result = ArrayList<NavigationItem>()

            @Suppress("UNCHECKED_CAST")
            for (arg in args) {
                result.addAll(arg as List<NavigationItem>)
            }

            result
        }
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
            if (configService.config().secretSanta()) {

                items.add(NavigationItem(
                        action = ActionType.SECRETSANTA,
                        title = getString(R.string.action_secret_santa),
                        icon = iconSecretSanta))
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
        return combineLatest(
                userService.loginState().map({ it.name }),
                extraCategories.categoriesAvailable,
                { username, extraCategory -> this.categoryNavigationItems(username, extraCategory) })
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

    class NavigationItem(val action: ActionType,
                         val title: String,
                         val icon: Drawable,
                         val layout: Int = R.layout.left_drawer_nav_item,
                         val filter: FeedFilter? = null,
                         val bookmark: Bookmark? = null,
                         val unreadCount: Int = 0) {

        fun hasFilter(): Boolean = filter != null
    }

    enum class ActionType {
        FILTER, BOOKMARK, MESSAGES, UPLOAD, FAVORITES, SECRETSANTA
    }
}
