package com.pr0gramm.app.services

import android.app.Activity
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.model.bookmark.Bookmark
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.model.user.LoginState
import com.pr0gramm.app.orm.asFeedFilter
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.ui.dialogs.ignoreError
import com.pr0gramm.app.util.RxPicasso
import com.pr0gramm.app.util.observeOnMainThread
import com.squareup.picasso.Picasso
import rx.Observable
import rx.Observable.combineLatest
import rx.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit

/**
 */
class NavigationProvider(
        private val context: Activity,
        private val userService: UserService,
        private val inboxService: InboxService,
        private val bookmarkService: BookmarkService,
        private val configService: ConfigService,
        private val singleShotService: SingleShotService,
        private val picasso: Picasso) {

    private val logger = Logger("NavigationProvider")

    private val iconBookmark by drawable(R.drawable.ic_action_bookmark)
    private val iconBookmarkTrending by drawable(R.drawable.ic_action_trending)
    private val iconFavorites by drawable(R.drawable.ic_action_favorite)
    private val iconFeedTypeBestOf by drawable(R.drawable.ic_action_bestof)
    private val iconFeedTypeControversial by drawable(R.drawable.ic_action_controversial)
    private val iconFeedTypeNew by drawable(R.drawable.ic_action_new)
    private val iconFeedTypePremium by drawable(R.drawable.ic_acton_stelz)
    private val iconFeedTypePromoted by drawable(R.drawable.ic_action_promoted)
    private val iconFeedTypeRandom by drawable(R.drawable.ic_action_random)
    private val iconInbox by drawable(R.drawable.ic_action_email)
    private val iconUpload by drawable(R.drawable.ic_action_upload)

    private val iconSettings by drawable(R.drawable.ic_action_settings)
    private val iconContact by drawable(R.drawable.ic_action_feedback)
    private val iconInvite by drawable(R.drawable.ic_action_invite)
    private val iconFAQ by drawable(R.drawable.ic_action_faq)
    private val iconPremium by drawable(R.drawable.ic_action_premium)
    private val iconLogin by drawable(R.drawable.ic_action_login)
    private val iconLogout by drawable(R.drawable.ic_action_logout)

    private val triggerNavigationUpdate = BehaviorSubject.create<Unit>(Unit)

    private fun drawable(@DrawableRes id: Int): Lazy<Drawable> {
        return lazy(LazyThreadSafetyMode.NONE) {
            AppCompatResources.getDrawable(context, id)!!
        }
    }

    fun navigationItems(currentSelection: Observable<FeedFilter?>): Observable<List<NavigationItem>> {
        val loginStates = userService.loginStates

        val items = loginStates.switchMap { loginState ->
            val rawSources = listOf<Observable<List<NavigationItem>>>(
                    remoteConfigItems(loginState, upper = true),

                    currentSelection.map { selection ->
                        categoryNavigationItems(selection, loginState.name)
                    },

                    remoteConfigItems(loginState, upper = false),

                    inboxService.unreadMessagesCount()
                            .startWith(Api.InboxCounts())
                            .map { listOf(inboxNavigationItem(it)) },

                    Observable.just(listOf(uploadNavigationItem)),

                    combineLatest(bookmarkService.observe(), currentSelection) { bookmarks, selection ->
                        bookmarksToNavItem(selection, bookmarks)
                    },

                    Observable.just(footerItems(loginState)))

            val sources = rawSources.map { navigationItemSource ->
                navigationItemSource
                        .startWith(emptyList<NavigationItem>())
                        .retryWhen { errorStream ->
                            errorStream
                                    .doOnNext { errValue -> logger.warn("Could not get category sub-items: ", errValue) }
                                    .delay(5, TimeUnit.SECONDS)
                        }
            }

            // observe and merge the menu items from different sources
            fun merge(args: Array<Any>): List<NavigationItem> {
                val result = mutableListOf<NavigationItem>()
                args.filterIsInstance<List<Any?>>().forEach { items ->
                    items.filterIsInstanceTo(result)
                }

                return result
            }

            combineLatest(sources, ::merge)
        }

        return triggerNavigationUpdate.switchMap { items }.distinctUntilChanged()
    }

    private fun footerItems(loginState: LoginState): List<NavigationItem> {
        val items = mutableListOf<NavigationItem>()

        items += staticItemDivider
        items += staticItemSettings
        items += staticItemContact

        if (loginState.authorized) {
            items += staticItemInvites
        }

        items += staticItemFAQ

        if (!loginState.authorized || !loginState.premium) {
            items += staticItemPremium
        }

        if (loginState.authorized) {
            items += staticItemLogout
        } else {
            items += staticItemLogin
        }

        return items
    }

    /**
     * Adds the default "fixed" items to the menu
     */
    private fun categoryNavigationItems(selection: FeedFilter?, username: String?): List<NavigationItem> {
        fun makeItem(title: String, icon: Drawable, filter: FeedFilter, action: ActionType = ActionType.FILTER) = NavigationItem(
                action = action, title = title, icon = icon, filter = filter, isSelected = filter == selection)

        val items = mutableListOf<NavigationItem>()

        items += makeItem(
                title = getString(R.string.action_feed_type_promoted),
                icon = iconFeedTypePromoted,
                filter = FeedFilter().withFeedType(FeedType.PROMOTED))

        items += makeItem(
                title = getString(R.string.action_feed_type_new),
                icon = iconFeedTypeNew,
                filter = FeedFilter().withFeedType(FeedType.NEW))

        val settings = Settings.get()

        if (settings.showCategoryBestOf) {
            items += makeItem(
                    title = getString(R.string.action_feed_type_bestof),
                    icon = iconFeedTypeBestOf,
                    filter = FeedFilter().withFeedType(FeedType.BESTOF))
        }

        if (settings.showCategoryControversial) {
            items += makeItem(
                    title = getString(R.string.action_feed_type_controversial),
                    icon = iconFeedTypeControversial,
                    filter = FeedFilter().withFeedType(FeedType.CONTROVERSIAL))
        }

        if (settings.showCategoryRandom) {
            items += makeItem(
                    title = getString(R.string.action_feed_type_random),
                    icon = iconFeedTypeRandom,
                    filter = FeedFilter().withFeedType(FeedType.RANDOM))
        }

        if (settings.showCategoryPremium && userService.userIsPremium) {
            items += makeItem(
                    title = getString(R.string.action_feed_type_premium),
                    icon = iconFeedTypePremium,
                    filter = FeedFilter().withFeedType(FeedType.PREMIUM))
        }

        if (username != null) {
            items += makeItem(
                    action = ActionType.FAVORITES,
                    title = getString(R.string.action_favorites),
                    icon = iconFavorites,
                    filter = FeedFilter().withFeedType(FeedType.NEW).withLikes(username))
        }

        return items
    }

    /**
     * Returns the menu item that takes the user to the inbox.
     */
    private fun inboxNavigationItem(unreadCounts: Api.InboxCounts): NavigationItem {
        return NavigationItem(
                action = ActionType.MESSAGES,
                title = getString(R.string.action_inbox),
                icon = iconInbox,
                layout = R.layout.left_drawer_nav_item_inbox,
                unreadCount = unreadCounts)
    }

    private fun bookmarksToNavItem(currentSelection: FeedFilter?, bookmarks: List<Bookmark>): List<NavigationItem> {
        val items = bookmarks
                .filter { userService.userIsPremium || it.asFeedFilter().feedType !== FeedType.PREMIUM }
                .mapTo(mutableListOf()) { entry ->
                    val icon = when {
                        entry.trending -> iconBookmarkTrending.constantState!!.newDrawable()
                        else -> iconBookmark.constantState!!.newDrawable()
                    }

                    val title = entry.title.toUpperCase()

                    val filter = entry.asFeedFilter()

                    NavigationItem(
                            action = ActionType.BOOKMARK,
                            title = title, icon = icon, bookmark = entry,
                            filter = filter, isSelected = filter == currentSelection)
                }

        val actionKey = "hint:bookmark-hold-to-delete:2"

        val hintNotYetShown = singleShotService.isFirstTime(actionKey)
        if (items.isNotEmpty() && bookmarkService.canEdit && hintNotYetShown) {
            val hint = NavigationItem(
                    action = ActionType.HINT,
                    title = getString(R.string.bookmark_hint_delete),
                    icon = null,
                    layout = R.layout.left_drawer_nav_item_hint,
                    customAction = {
                        singleShotService.markAsDoneOnce(actionKey)
                        triggerNavigationUpdate.onNext(Unit)
                    })

            items.add(0, hint)
        }

        if (items.isNotEmpty()) {
            items.add(0, staticItemDivider)
        }

        return items
    }

    /**
     * Returns the menu item that takes the user to the upload activity.
     */
    private val uploadNavigationItem: NavigationItem = NavigationItem(
            action = ActionType.UPLOAD,
            title = getString(R.string.action_upload),
            icon = iconUpload)

    /**
     * Divider to divide item groups
     */
    private val staticItemDivider: NavigationItem = NavigationItem(ActionType.DIVIDER,
            layout = R.layout.left_drawer_nav_item_divider)

    private fun staticItem(action: ActionType, icon: Drawable, title: String): NavigationItem {
        return NavigationItem(action, title = title, icon = icon, colorOverride = 0x80808080.toInt())
    }

    private val staticItemFAQ: NavigationItem = staticItem(
            ActionType.FAQ, iconFAQ, getString(R.string.action_faq))

    private val staticItemSettings: NavigationItem = staticItem(
            ActionType.SETTINGS, iconSettings, getString(R.string.action_settings))

    private val staticItemInvites: NavigationItem = staticItem(
            ActionType.INVITES, iconInvite, getString(R.string.action_invite))

    private val staticItemContact: NavigationItem = staticItem(
            ActionType.CONTACT, iconContact, getString(R.string.action_contact))

    private val staticItemPremium: NavigationItem = staticItem(
            ActionType.PREMIUM, iconPremium, getString(R.string.action_premium))

    private val staticItemLogin: NavigationItem = staticItem(
            ActionType.LOGIN, iconLogin, getString(R.string.action_login))

    private val staticItemLogout: NavigationItem = staticItem(
            ActionType.LOGOUT, iconLogout, getString(R.string.action_logout))

    /**
     * Short for context.getString(...)
     */
    private fun getString(id: Int): String {
        return context.getString(id)
    }

    private fun remoteConfigItems(loginState: LoginState, upper: Boolean): Observable<List<NavigationItem>> {
        return configService.observeConfig()
                .map { config -> config.specialMenuItems }
                .distinctUntilChanged()
                .switchMap { item -> resolveRemoteConfigItems(loginState, upper, item) }
    }

    /**
     * Resolves the special for the given config and loads the icon,
     * if there is one.
     */
    private fun resolveRemoteConfigItems(
            loginState: LoginState, upper: Boolean,
            items: List<Config.MenuItem>): Observable<List<NavigationItem>> {

        return Observable.from(items)
                .concatMapEager<NavigationItem> { item ->
                    if (item.requireLogin && !loginState.authorized || item.lower != !upper) {
                        return@concatMapEager Observable.empty()
                    }

                    loadMenuItem(item)
                }
                .toList()
    }

    private fun loadMenuItem(item: Config.MenuItem): Observable<NavigationItem> {
        logger.debug { "Loading item $item" }

        return Observable.just(item)

                // Picasso load call need to be on the main thread.
                .observeOnMainThread()

                .flatMap {
                    RxPicasso.load(picasso, picasso.load(Uri.parse(item.icon))
                            .noPlaceholder()
                            .resize(iconUpload.intrinsicWidth, iconUpload.intrinsicHeight))
                }
                .map { bitmap ->
                    logger.info { "Loaded image for $item" }
                    val icon = BitmapDrawable(context.resources, bitmap)
                    val uri = Uri.parse(item.link)

                    val layout = if (item.noHighlight) R.layout.left_drawer_nav_item else R.layout.left_drawer_nav_item_special
                    NavigationItem(ActionType.URI, item.name, icon, uri = uri, layout = layout)
                }
                .retryWhen { err ->
                    err.doOnNext { logger.warn(it) { "could not load item" } }.zipWith(Observable.range(1, 3)) { _, i -> i }.flatMap { idx ->
                        logger.debug { "Delay retry by $idx second(s) because of $err" }
                        Observable.timer(idx.toLong(), TimeUnit.SECONDS)
                    }
                }
                .ignoreError()
    }

    class NavigationItem(val action: ActionType,
                         val title: String? = null,
                         val icon: Drawable? = null,
                         val layout: Int = R.layout.left_drawer_nav_item,
                         val filter: FeedFilter? = null,
                         val bookmark: Bookmark? = null,
                         val unreadCount: Api.InboxCounts = Api.InboxCounts(),
                         val customAction: () -> Unit = {},
                         val uri: Uri? = null,
                         val isSelected: Boolean = false,
                         val colorOverride: Int? = null) {

        private val hashCode by lazy(LazyThreadSafetyMode.NONE) {
            listOf(action, title, layout, filter, bookmark, unreadCount, uri, isSelected).hashCode()
        }

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
                    && other.isSelected == isSelected
        }
    }

    enum class ActionType {
        HINT, FILTER, BOOKMARK, MESSAGES, UPLOAD, FAVORITES, URI,
        DIVIDER,
        SETTINGS, CONTACT, INVITES, FAQ, PREMIUM, LOGIN, LOGOUT
    }
}
