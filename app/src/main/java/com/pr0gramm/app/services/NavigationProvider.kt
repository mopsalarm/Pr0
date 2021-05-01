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
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runInterruptible
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 */
@OptIn(ExperimentalTime::class)
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
    private val iconCollections by drawable(R.drawable.ic_collection_yes)
    private val iconFeedTypeBestOf by drawable(R.drawable.ic_action_bestof)
    private val iconFeedTypeControversial by drawable(R.drawable.ic_action_controversial)
    private val iconFeedTypeNew by drawable(R.drawable.ic_action_new)
    private val iconFeedTypePremium by drawable(R.drawable.ic_action_follow_full)
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

    // set value to true to trigger a refresh of the flow once.
    private val refreshAfterNavItemWasDeletedStateFlow = MutableStateFlow(false)

    private fun drawable(@DrawableRes id: Int): Lazy<Drawable> {
        return lazy {
            AppCompatResources.getDrawable(context, id)!!
        }
    }

    fun navigationItems(currentSelection: Flow<FeedFilter?>): Flow<List<NavigationItem>> {
        val loginStates = userService.loginStates

        val items = loginStates.flatMapLatest { loginState ->
            val rawSources = listOf(
                    remoteConfigItems(loginState, upper = true),

                    currentSelection.map { selection ->
                        categoryNavigationItems(selection, loginState.name)
                    },

                    remoteConfigItems(loginState, upper = false),

                    inboxService.unreadMessagesCount()
                            .map { listOf(inboxNavigationItem(it)) },

                    flowOf(listOf(uploadNavigationItem)),

                    bookmarkService.observe().combine(currentSelection) { bookmarks, selection ->
                        bookmarksToNavItem(selection, bookmarks)
                    },

                    flowOf(footerItems(loginState)))

            val sources = rawSources.map { source ->
                source.onStart { emit(listOf()) }.retryWhen { err, _ ->
                    logger.warn("Could not get category sub-items, retrying soon: ", err)
                    delay(Duration.seconds(5))
                    true
                }
            }

            // flatten generated lists into one list.
            combine(sources) { values -> values.flatMap { it } }
        }

        return refreshAfterNavItemWasDeletedStateFlow.flatMapLatest { items }.distinctUntilChanged()
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

        items += makeItem(
                title = getString(R.string.action_feed_type_bestof),
                icon = iconFeedTypeBestOf,
                filter = FeedFilter().withFeedType(FeedType.BESTOF))

        if (Settings.showCategoryControversial) {
            items += makeItem(
                    title = getString(R.string.action_feed_type_controversial),
                    icon = iconFeedTypeControversial,
                    filter = FeedFilter().withFeedType(FeedType.CONTROVERSIAL))
        }

        if (Settings.showCategoryRandom) {
            items += makeItem(
                    title = getString(R.string.action_feed_type_random),
                    icon = iconFeedTypeRandom,
                    filter = FeedFilter().withFeedType(FeedType.RANDOM))
        }

        if (Settings.showCategoryStalk) {
            items += makeItem(
                    title = getString(R.string.action_feed_type_premium),
                    icon = iconFeedTypePremium,
                    filter = FeedFilter().withFeedType(FeedType.STALK))
        }

        if (username != null) {
            items += makeItem(
                    action = ActionType.COLLECTIONS,
                    title = getString(R.string.action_collections),
                    icon = iconCollections,
                    filter = FeedFilter()
                            .withFeedType(FeedType.NEW)
                            .withCollection(username, "**ANY", "**ANY"))
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
                .filter { userService.userIsPremium || it.asFeedFilter().feedType !== FeedType.STALK }
                .mapTo(mutableListOf()) { entry ->

                    val filter = entry.asFeedFilter()
                    val filterInverse = filter.invert()

                    val icon = when {
                        entry.trending -> iconBookmarkTrending.constantState!!.newDrawable()
                        else -> iconBookmark.constantState!!.newDrawable()
                    }

                    val layoutId = when {
                        entry.trending && filterInverse != null -> R.layout.left_drawer_nav_item_trending
                        else -> R.layout.left_drawer_nav_item
                    }

                    val title = entry.title

                    val isSelected = filter == currentSelection || filterInverse == currentSelection

                    NavigationItem(
                            action = ActionType.BOOKMARK, layout = layoutId,
                            title = title, icon = icon, bookmark = entry,
                            filter = filter, filterInverse = filterInverse,
                            isSelected = isSelected)
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
                        refreshAfterNavItemWasDeletedStateFlow.value = true
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
    private val uploadNavigationItem: NavigationItem by lazy {
        NavigationItem(
                action = ActionType.UPLOAD,
                title = getString(R.string.action_upload),
                icon = iconUpload)
    }

    /**
     * Divider to divide item groups
     */
    private val staticItemDivider: NavigationItem by lazy {
        NavigationItem(ActionType.DIVIDER,
                layout = R.layout.left_drawer_nav_item_divider)
    }

    private fun staticItem(action: ActionType, icon: Drawable, title: String): NavigationItem {
        return NavigationItem(action, title = title, icon = icon, colorOverride = 0x80808080.toInt())
    }

    private val staticItemFAQ: NavigationItem by lazy {
        staticItem(ActionType.FAQ, iconFAQ, getString(R.string.action_faq))
    }

    private val staticItemSettings: NavigationItem by lazy {
        staticItem(ActionType.SETTINGS, iconSettings, getString(R.string.action_settings))
    }

    private val staticItemInvites: NavigationItem by lazy {
        staticItem(ActionType.INVITES, iconInvite, getString(R.string.action_invite))
    }

    private val staticItemContact: NavigationItem by lazy {
        staticItem(ActionType.CONTACT, iconContact, getString(R.string.action_contact))
    }

    private val staticItemPremium: NavigationItem by lazy {
        staticItem(ActionType.PREMIUM, iconPremium, getString(R.string.action_premium))
    }

    private val staticItemLogin: NavigationItem by lazy {
        staticItem(ActionType.LOGIN, iconLogin, getString(R.string.action_login))
    }

    private val staticItemLogout: NavigationItem by lazy {
        staticItem(ActionType.LOGOUT, iconLogout, getString(R.string.action_logout))
    }

    /**
     * Short for context.getString(...)
     */
    private fun getString(id: Int): String {
        return context.getString(id)
    }

    private fun remoteConfigItems(loginState: LoginState, upper: Boolean): Flow<List<NavigationItem>> {
        return configService.observeConfig()
                .map { config -> config.specialMenuItems }
                .distinctUntilChanged()
                .flatMapLatest { item -> resolveRemoteConfigItems(loginState, upper, item) }

    }

    /**
     * Resolves the special for the given config and loads the icon,
     * if there is one.
     */
    private fun resolveRemoteConfigItems(
            loginState: LoginState, upper: Boolean,
            items: List<Config.MenuItem>): Flow<List<NavigationItem>> {

        val images = flow {
            val itemsToLoad = items.filterNot { item ->
                item.requireLogin && !loginState.authorized || item.lower != !upper
            }

            val navItems = runInterruptible(Dispatchers.IO) {
                itemsToLoad.map { item ->
                    logger.debug { "Loading item $item" }

                    val bitmap = picasso.load(Uri.parse(item.icon))
                            .noPlaceholder()
                            .resize(iconUpload.intrinsicWidth, iconUpload.intrinsicHeight)
                            .get()

                    logger.info { "Loaded image for $item" }
                    val icon = BitmapDrawable(context.resources, bitmap)
                    val uri = Uri.parse(item.link)

                    val layout = if (item.noHighlight) R.layout.left_drawer_nav_item else R.layout.left_drawer_nav_item_special
                    NavigationItem(ActionType.URI, item.name, icon, uri = uri, layout = layout)
                }
            }

            emit(navItems)
        }

        return images.retry(retries = 3).catch { err ->
            logger.warn(err) { "Could not load item after 3 retries." }
        }
    }

    class NavigationItem(val action: ActionType,
                         val title: CharSequence? = null,
                         val icon: Drawable? = null,
                         val layout: Int = R.layout.left_drawer_nav_item,
                         val filter: FeedFilter? = null,
                         val filterInverse: FeedFilter? = null,
                         val bookmark: Bookmark? = null,
                         val unreadCount: Api.InboxCounts = Api.InboxCounts(),
                         val customAction: () -> Unit = {},
                         val uri: Uri? = null,
                         val isSelected: Boolean = false,
                         val colorOverride: Int? = null) {

        private val hashCode by lazy {
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
        HINT, FILTER, BOOKMARK, MESSAGES, UPLOAD, COLLECTIONS, URI,
        DIVIDER,
        SETTINGS, CONTACT, INVITES, FAQ, PREMIUM, LOGIN, LOGOUT
    }
}
