package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.ScrollView
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import com.google.common.base.CharMatcher
import com.google.common.base.Objects.equal
import com.google.common.base.Throwables
import com.jakewharton.rxbinding.support.design.widget.dismisses
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.MessageConverter
import com.pr0gramm.app.feed.*
import com.pr0gramm.app.feed.ContentType.*
import com.pr0gramm.app.services.*
import com.pr0gramm.app.services.config.Config
import com.pr0gramm.app.services.preloading.PreloadManager
import com.pr0gramm.app.services.preloading.PreloadService
import com.pr0gramm.app.ui.*
import com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity
import com.pr0gramm.app.ui.back.BackAwareFragment
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment
import com.pr0gramm.app.ui.dialogs.PopupPlayerFactory
import com.pr0gramm.app.ui.dialogs.ignoreError
import com.pr0gramm.app.ui.views.CustomSwipeRefreshLayout
import com.pr0gramm.app.ui.views.SearchOptionsView
import com.pr0gramm.app.ui.views.UserInfoView
import com.pr0gramm.app.util.*
import com.squareup.moshi.JsonEncodingException
import com.squareup.picasso.Picasso
import com.trello.rxlifecycle.android.FragmentEvent
import org.joda.time.Duration
import org.joda.time.Instant
import org.slf4j.LoggerFactory
import rx.Observable
import rx.android.schedulers.AndroidSchedulers.mainThread
import rx.subjects.PublishSubject
import java.net.ConnectException
import java.util.*
import java.util.concurrent.TimeUnit

/**
 */
class FeedFragment : BaseFragment("FeedFragment"), FilterFragment, BackAwareFragment {
    private val settings = Settings.get()

    private val feedService: FeedService by instance()
    private val picasso: Picasso by instance()
    private val seenService: SeenService by instance()
    private val bookmarkService: BookmarkService by instance()
    private val userService: UserService by instance()
    private val singleShotService: SingleShotService by instance()
    private val inMemoryCacheService: InMemoryCacheService by instance()
    private val preloadManager: PreloadManager by instance()
    private val inboxService: InboxService by instance()
    private val recentSearchesServices: RecentSearchesServices by instance()
    private val followService: StalkService by instance()
    private val adService: AdService by instance()
    private val config: Config by instance()

    private val recyclerView: RecyclerView by bindView(R.id.list)
    private val swipeRefreshLayout: CustomSwipeRefreshLayout by bindView(R.id.refresh)
    private val noResultsView: View by bindView(R.id.empty)
    private val errorLoadingFeedView: View by bindView(R.id.error)
    private val searchContainer: ScrollView by bindView(R.id.search_container)
    private val searchView: SearchOptionsView by bindView(R.id.search_options)

    private var quickPeekDialog: Dialog? = null

    private val doIfAuthorizedHelper = LoginActivity.helper(this)

    private var bookmarkable: Boolean = false
    private var autoScrollOnLoad: Long? = null
    private var autoOpenOnLoad: ItemWithComment? = null

    private lateinit var loader: FeedManager

    private val feedAdapter by lazy {
        FeedAdapter(picasso,
                userHintClickedListener = { name -> openUserUploads(name) },
                userActionListener = UserActionListener())
    }

    private val activeUsername: String? get() = state.userInfo?.info?.user?.name

    private var scrollToolbar: Boolean = false

    private val actionHandler: MainActionHandler get() = activity as MainActionHandler

    private data class State(
            val feedItems: List<FeedItem>,
            val feedFilter: FeedFilter,
            val preloadedCount: Int = 0,
            val ownUsername: String? = null,
            val userInfo: UserInfo? = null,
            val adsVisible: Boolean = false,
            val seenIndicatorStyle: IndicatorStyle = IndicatorStyle.NONE,
            val userInfoCommentsOpen: Boolean = false)

    private var feed: Feed by observeChangeEx(Feed()) { old, new ->
        if (old == new) {
            logger.info("No change in feed items.")

        } else {
            logger.debug("Feed before update: {} items, oldest={}, newest={}",
                    old.size, old.oldest?.id, old.newest?.id)

            logger.debug("Feed after update: {} items, oldest={}, newest={}",
                    new.size, new.oldest?.id, new.newest?.id)

            state = state.copy(feedItems = feed.items, feedFilter = feed.filter)
        }

        if (new.isNotEmpty()) {
            performAutoOpen()
        }
    }

    private var state by observeChangeEx(State(feed.items, feed.filter)) { _, state ->
        val filter = state.feedFilter

        val entries = mutableListOf<FeedAdapter.Entry>()

        logger.time("Update adapter") {
            // add a little spacer to the top to account for the action bar
            if (useToolbarTopMargin()) {
                val offset = AndroidUtility.getActionBarContentOffset(context)
                if (offset > 0) {
                    entries += FeedAdapter.Entry.Spacer(1, height = offset)
                }
            }

            if (state.userInfo != null) {
                val userInfo = state.userInfo
                val isSelfInfo = isSelfInfo(userInfo.info)

                // if we found this user using a normal 'search', we will show a hint
                // that the user exists
                if (filter.tags != null) {
                    if (!isSelfInfo) {
                        val userAndMark = userInfo.info.user.run { UserAndMark(name, mark) }
                        entries += FeedAdapter.Entry.UserHint(userAndMark)
                    }

                } else {
                    entries += FeedAdapter.Entry.User(state.userInfo, isSelfInfo)

                    if (state.userInfoCommentsOpen) {
                        val user = userService.name
                        userInfo.comments.mapTo(entries) { comment ->
                            val msg = MessageConverter.of(state.userInfo.info.user, comment)
                            FeedAdapter.Entry.Comment(msg, user)
                        }
                    }

                    entries += FeedAdapter.Entry.Spacer(2, layout = R.layout.user_info_footer)
                }

            } else if (filter.username != null) {
                val item = state.feedItems.firstOrNull { it.user.equals(filter.username, ignoreCase = true) }
                if (item != null) {
                    val user = UserAndMark(item.user, item.mark)
                    entries += FeedAdapter.Entry.UserLoading(user)
                    entries += FeedAdapter.Entry.Spacer(2, layout = R.layout.user_info_footer)
                }
            }

            if (!state.userInfoCommentsOpen) {
                // check if we need to check if the posts are 'seen'
                val markAsSeen = state.seenIndicatorStyle === IndicatorStyle.ICON && !(
                        state.ownUsername != null && state.ownUsername.equals(filter.likes
                                ?: filter.username, ignoreCase = true))

                // always show at least one ad banner - e.g. during load
                if (state.adsVisible && state.feedItems.isEmpty()) {
                    entries += FeedAdapter.Entry.Ad(0)
                }

                for ((idx, item) in state.feedItems.withIndex()) {
                    val id = item.id
                    val seen = markAsSeen && seenService.isSeen(id)
                    val repost = inMemoryCacheService.isRepost(id)
                    val preloaded = preloadManager.exists(id)

                    // show an ad banner every ~50 lines
                    if (state.adsVisible && (idx % (50 * thumbnailColumCount)) == 0) {
                        entries += FeedAdapter.Entry.Ad(idx.toLong())
                    }

                    entries += FeedAdapter.Entry.Item(item, repost, preloaded, seen)
                }
            }

            feedAdapter.submitList(entries)
        }
    }

    /**
     * Initialize a new feed fragment.
     */
    init {
        setHasOptionsMenu(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // initialize auto opening (only on first start)
        if (savedInstanceState == null) {
            val start = arguments?.getParcelable<ItemWithComment?>(ARG_FEED_START)
            if (start != null) {
                logger.debug("Requested to open item {} on load", start)
                autoScrollOnLoad = start.itemId
                autoOpenOnLoad = start
            }
        }

        // initialize auto scroll if we got restored
        savedInstanceState?.getLong(ARG_FEED_SCROLL, 0).takeIf { it != 0L }?.let { id ->
            logger.debug("Requested to scroll to item {} on load", id)
            autoScrollOnLoad = id
        }

        this.scrollToolbar = useToolbarTopMargin()

        val bundle = savedInstanceState?.getByteArray(ARG_FEED)
        val previousFeed = bundle?.let { Feed.restore(it) }

        val feed = previousFeed ?: Feed(filterArgument, selectedContentType)
        loader = FeedManager(feedService, feed)

        if (previousFeed == null) {
            loader.restart(around = autoScrollOnLoad)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        return inflater.inflate(R.layout.fragment_feed, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity()

        // prepare the list of items
        val spanCount = thumbnailColumCount
        recyclerView.itemAnimator = null
        recyclerView.adapter = feedAdapter
        recyclerView.layoutManager = GridLayoutManager(activity, spanCount).apply {
            spanSizeLookup = feedAdapter.SpanSizeLookup(spanCount)
        }

        recyclerView.addOnScrollListener(onScrollListener)

        // we can still swipe up if we are not at the start of the feed.
        swipeRefreshLayout.canSwipeUpPredicate = { !feed.isAtStart }

        swipeRefreshLayout.setColorSchemeResources(ThemeHelper.accentColor)

        swipeRefreshLayout.setOnRefreshListener {
            logger.debug("onRefresh called for swipe view.")
            if (feed.isAtStart) {
                refreshFeed()
            } else {
                // do not refresh
                swipeRefreshLayout.isRefreshing = false
            }
        }

        if (useToolbarTopMargin()) {
            // use height of the toolbar to configure swipe refresh layout.
            val abHeight = AndroidUtility.getActionBarContentOffset(activity)
            val offset = AndroidUtility.getStatusBarHeight(activity)
            swipeRefreshLayout.setProgressViewOffset(false, offset, (offset + 1.5 * (abHeight - offset)).toInt())
        }

        resetToolbar()

        createRecyclerViewClickListener()

        // observe changes so we can update the menu
        followService.changes()
                .observeOnMain()
                .compose(bindToLifecycle<String>())
                .filter { name -> name.equals(activeUsername, ignoreCase = true) }
                .subscribe { activity.invalidateOptionsMenu() }

        // execute a search when we get a search term
        searchView.searchQuery().compose(bindToLifecycle()).subscribe { this.performSearch(it) }
        searchView.searchCanceled().compose(bindToLifecycle()).subscribe { hideSearchContainer() }
        searchView.setupAutoComplete(recentSearchesServices)

        // restore open search
        if (savedInstanceState != null && savedInstanceState.getBoolean("searchContainerVisible")) {
            showSearchContainer(false)
        }

        // close search on click into the darkened area.
        searchContainer.setOnTouchListener(DetectTapTouchListener { hideSearchContainer() })

        // lets start receiving feed updates
        subscribeToFeedUpdates()

        queryForUserInfo()

        // start showing ads.
        adService.enabledForType(Config.AdType.FEED)
                .observeOn(mainThread())
                .compose(bindToLifecycle())
                .subscribe { show -> state = state.copy(adsVisible = show) }
    }

    private fun subscribeToFeedUpdates() {
        loader.updates.compose(bindToLifecycle()).subscribe { update ->
            logger.info("Got update {}", update)

            when (update) {
                is FeedManager.Update.NewFeed -> {
                    refreshRepostInfos(feed, update.feed)

                    feed = update.feed
                    showNoResultsTextView(update.remote && update.feed.isEmpty())
                    showErrorLoadingFeedView(false)
                }

                is FeedManager.Update.Error -> {
                    onFeedError(update.err)
                    showErrorLoadingFeedView(true)
                }

                is FeedManager.Update.LoadingStarted -> {
                    swipeRefreshLayout.isRefreshing = true

                    showNoResultsTextView(false)
                    showErrorLoadingFeedView(false)
                }


                is FeedManager.Update.LoadingStopped ->
                    swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (view != null) {
            val lastVisibleItem = findLastVisibleFeedItem()
            val lastVisibleIndex = lastVisibleItem?.let { feed.indexById(it.id) }

            if (lastVisibleItem != null && lastVisibleIndex != null) {
                logger.debug("Store feed around item {} ({})", lastVisibleItem.id, feed)
                outState.putByteArray(ARG_FEED, feed.persist(lastVisibleIndex))
                outState.putLong(ARG_FEED_SCROLL, lastVisibleItem.id)
            }

            outState.putBoolean("searchContainerVisible", searchContainerIsVisible())
        }
    }

    private fun initialSearchViewState(): Bundle? {
        return arguments?.getBundle(ARG_SEARCH_QUERY_STATE) ?: run {
            currentFilter.tags?.let { tags ->
                SearchOptionsView.ofQueryTerm(tags)
            }
        }
    }

    private fun queryForUserInfo() {
        if (!isNormalMode) {
            return
        }

        queryUserInfo().take(1).compose(bindToLifecycle()).ignoreError().subscribe { value ->
            state = state.copy(userInfo = value)
            activity?.invalidateOptionsMenu()
        }
    }

    private fun useToolbarTopMargin(): Boolean {
        return isNormalMode
    }

    private val isNormalMode: Boolean
        get() {
            return arguments?.getBoolean(ARG_NORMAL_MODE, true) ?: true
        }

    private inner class UserActionListener : UserInfoView.UserActionListener {
        override fun onWriteMessageClicked(userId: Int, name: String) {
            doIfAuthorizedHelper.run {
                startActivity(WriteMessageActivity.intent(context, userId.toLong(), name))
            }
        }

        override fun onUserFavoritesClicked(name: String) {
            val filter = currentFilter.basic().withLikes(name)
            if (filter != currentFilter) {
                (activity as MainActionHandler).onFeedFilterSelected(filter)
            }

            state = state.copy(userInfoCommentsOpen = false)
        }

        override fun onShowUploadsClicked(name: String) {
            val filter = currentFilter.basic().withFeedType(FeedType.NEW).withUser(name)
            if (filter != currentFilter) {
                (activity as MainActionHandler).onFeedFilterSelected(filter)
            }

            state = state.copy(userInfoCommentsOpen = false)
        }

        override fun onShowCommentsClicked() {
            state = state.copy(userInfoCommentsOpen = true)
        }

    }

    private fun openUserUploads(name: String) {
        actionHandler.onFeedFilterSelected(currentFilter.basic()
                .withFeedType(FeedType.NEW)
                .withUser(name))
    }

    private fun queryUserInfo(): Observable<UserInfo> {
        val filter = filterArgument

        val queryString = filter.username ?: filter.tags ?: filter.likes

        if (queryString != null && queryString.matches("[A-Za-z0-9]{2,}".toRegex())) {
            val contentTypes = selectedContentType

            val cached = inMemoryCacheService.getUserInfo(contentTypes, queryString)
            if (cached != null) {
                return Observable.just(cached)
            }

            val first = userService
                    .info(queryString, contentTypes)
                    .subscribeOnBackground()
                    .doOnNext { info -> followService.markAsFollowing(info.user.name, info.following) }
                    .onErrorResumeEmpty()

            val second = inboxService
                    .getUserComments(queryString, contentTypes)
                    .subscribeOnBackground()
                    .map { it.comments }
                    .onErrorReturn { listOf() }

            return Observable
                    .zip(first, second, ::UserInfo)
                    .doOnNext { info -> inMemoryCacheService.cacheUserInfo(contentTypes, info) }
                    .observeOnMain()

        } else {
            return Observable.empty()
        }
    }

    override fun onDestroyView() {
        recyclerView.removeOnScrollListener(onScrollListener)
        super.onDestroyView()
    }

    override fun onDestroy() {
        // destroy any ad views that might still exist
        feedAdapter.destroyAdView()

        super.onDestroy()
    }

    private fun resetToolbar() {
        val activity = activity
        if (activity is ToolbarActivity) {
            activity.scrollHideToolbarListener.reset()
        }
    }

    private fun hideToolbar() {
        val activity = activity
        if (activity is ToolbarActivity) {
            activity.scrollHideToolbarListener.hide()
        }
    }

    private fun onBookmarkableStateChanged(bookmarkable: Boolean) {
        this.bookmarkable = bookmarkable
        activity?.invalidateOptionsMenu()
    }

    private val filterArgument: FeedFilter by fragmentArgument(name = ARG_FEED_FILTER)

    private val selectedContentType: EnumSet<ContentType>
        get() {
            if (!userService.isAuthorized)
                return EnumSet.of(SFW)

            return settings.contentType
        }

    override fun onResume() {
        super.onResume()

        if (config.trackItemView) {
            Track.screen(activity, "Feed")
        }

        // check if we should show the pin button or not.
        if (settings.showPinButton) {
            bookmarkService.isBookmarkable(currentFilter)
                    .compose(bindToLifecycleAsync())
                    .subscribe({ onBookmarkableStateChanged(it) }, {})
        }

        recheckContentTypes()

        // force re-apply the state
        run {
            var state = this.state

            // set new indicator style
            if (state.seenIndicatorStyle !== settings.seenIndicatorStyle) {
                state = state.copy(seenIndicatorStyle = settings.seenIndicatorStyle)
            }

            if (state.ownUsername != userService.name) {
                state = state.copy(ownUsername = userService.name)
            }

            this.state = state
        }

        if (feed.created.isBefore(Instant.now().minus(Duration.standardSeconds(1)))) {
            checkForNewItems()
        }

        // Observe all preloaded items to get them into the cache and to show the
        // correct state in the ui once they are loaded
        preloadManager.all()
                .throttleLast(5, TimeUnit.SECONDS)
                .compose(bindToLifecycleAsync())
                .ignoreError()
                .subscribe { state = state.copy(preloadedCount = it.size) }
    }

    private fun checkForNewItems() {
        if (!feed.isAtStart || feed.filter.feedType == FeedType.RANDOM) {
            logger.info("Not checking for new items as we are not at the beginning of the feed")
            return
        }

        (recyclerView.layoutManager as? LinearLayoutManager?)?.let { manager ->
            if (manager.findFirstVisibleItemPosition() > 32) {
                logger.info("Not checking for new items as we are not scrolled to the top")
                return
            }
        }

        logger.info("Checking for new items in current feed")

        val query = FeedService.FeedQuery(feed.filter, feed.contentType)
        feedService.load(query)
                .map { response ->
                    // count the number of new ids in the loaded feed items
                    val previousIds = feed.mapTo(mutableSetOf()) { it.id }
                    response.items.count { it.id !in previousIds }
                }
                .onErrorResumeEmpty()
                .compose(bindToLifecycleAsync())
                .filter { it > 0 && feed.filter == query.filter }
                .subscribe { itemCount -> newItemsSnackbar(itemCount) }
    }

    private fun newItemsSnackbar(itemCount: Int) {
        val text = when {
            itemCount == 1 -> getString(R.string.hint_new_items_one)
            itemCount <= 16 -> getString(R.string.hint_new_items_some, itemCount)
            else -> getString(R.string.hint_new_items_many)
        }

        val snackbar = Snackbar.make(view!!, text, Snackbar.LENGTH_LONG).apply {
            configureNewStyle()
            setAction(R.string.hint_refresh_load) { refreshFeed() }
            show()
        }

        // dismiss once the fragment stops.
        lifecycle().filter { it == FragmentEvent.STOP }
                .takeUntil(snackbar.dismisses())
                .subscribe { snackbar.dismiss() }
    }

    private fun recheckContentTypes() {
        // check if content type has changed, and reload if necessary
        val feedFilter = feed.filter
        val newContentType = selectedContentType
        if (feed.contentType != newContentType) {
            replaceFeedFilter(feedFilter, newContentType)
        }
    }

    private fun replaceFeedFilter(feedFilter: FeedFilter, newContentType: Set<ContentType>, item: Long? = null) {
        autoScrollOnLoad = item ?: autoOpenOnLoad?.itemId
                ?: findLastVisibleFeedItem(newContentType)?.id

        // set a new adapter if we have a new content type
        // this clears the current feed immediately
        loader.reset(Feed(feedFilter, newContentType))
        loader.restart(around = autoScrollOnLoad)

        activity?.invalidateOptionsMenu()
    }

    /**
     * Finds the last item in the feed that is visible and of one of the given content types

     * @param contentType The target-content type.
     */
    private fun findLastVisibleFeedItem(
            contentType: Set<ContentType> = ContentType.AllSet): FeedItem? {

        val items = feedAdapter.items.takeUnless { it.isEmpty() } ?: return null

        val layoutManager = recyclerView.layoutManager as? GridLayoutManager
        return layoutManager?.let {
            // if the first row is visible, skip this stuff.
            val firstCompletelyVisible = layoutManager.findFirstCompletelyVisibleItemPosition()
            if (firstCompletelyVisible == 0 || firstCompletelyVisible == RecyclerView.NO_POSITION)
                return null

            val lastCompletelyVisible = layoutManager.findLastCompletelyVisibleItemPosition()
            if (lastCompletelyVisible == RecyclerView.NO_POSITION)
                return null

            val idx = (lastCompletelyVisible).coerceIn(items.indices)
            items.take(idx)
                    .mapNotNull { (it as? FeedAdapter.Entry.Item)?.item }
                    .lastOrNull { contentType.contains(it.contentType) }
        }
    }

    /**
     * Depending on whether the screen is landscape or portrait, and how large
     * the screen is, we show a different number of items per row.
     */
    private val thumbnailColumCount: Int by lazy(LazyThreadSafetyMode.NONE) {
        val config = resources.configuration
        val portrait = config.screenWidthDp < config.screenHeightDp

        val screenWidth = config.screenWidthDp
        Math.min((screenWidth / 120.0 + 0.5).toInt(), if (portrait) 5 else 7)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_feed, menu)

        // hide search item, if we are not searchable
        menu.findItem(R.id.action_search)?.let { item ->
            item.isVisible = currentFilter.feedType.searchable
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val activity = activity ?: return

        val filter = currentFilter
        val feedType = filter.feedType

        menu.findItem(R.id.action_refresh)
                ?.isVisible = settings.showRefreshButton

        menu.findItem(R.id.action_pin)
                ?.isVisible = bookmarkable

        menu.findItem(R.id.action_preload)
                ?.isVisible = feedType.preloadable

        menu.findItem(R.id.action_block_user)
                ?.isVisible = userService.userIsAdmin && activeUsername != null

        menu.findItem(R.id.action_feedtype)?.let { item ->
            item.isVisible = !filter.isBasic

            item.setTitle(if (switchFeedTypeTarget(filter) === FeedType.PROMOTED)
                R.string.action_switch_to_top else R.string.action_switch_to_new)
        }

        menu.findItem(R.id.action_change_content_type)?.let { item ->
            if (userService.isAuthorized) {
                val icon = ContentTypeDrawable(activity, selectedContentType)
                icon.textSize = resources.getDimensionPixelSize(
                        R.dimen.feed_content_type_action_icon_text_size).toFloat()

                item.icon = icon
                item.isVisible = true

                updateContentTypeItems(menu)

            } else {
                item.isVisible = false
            }
        }


        val follow = menu.findItem(R.id.action_follow)
        val unfollow = menu.findItem(R.id.action_unfollow)
        val bookmark = menu.findItem(R.id.action_pin)
        if (follow != null && unfollow != null && bookmark != null) {
            // go to default state.
            follow.isVisible = false
            unfollow.isVisible = false

            activeUsername?.let { activeUsername ->
                if (userService.isPremiumUser) {
                    val following = followService.isFollowing(activeUsername)
                    follow.isVisible = !following
                    unfollow.isVisible = following
                }

                // never bookmark a user
                bookmark.isVisible = false
            }
        }
    }

    private fun switchFeedTypeTarget(filter: FeedFilter): FeedType {
        return if (filter.feedType !== FeedType.PROMOTED) FeedType.PROMOTED else FeedType.NEW
    }

    private fun updateContentTypeItems(menu: Menu) {
        // only one content type selected?
        val withoutImplicits = settings.contentType.withoutImplicit()
        val single = withoutImplicits.size == 1

        val types = mapOf(
                R.id.action_content_type_sfw to settings.contentTypeSfw,
                R.id.action_content_type_nsfw to settings.contentTypeNsfw,
                R.id.action_content_type_nsfl to settings.contentTypeNsfl)

        for ((key, value) in types) {
            menu.findItem(key)?.let { item ->
                item.isChecked = value
                item.isEnabled = !single || !value
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val contentTypes = mapOf(
                R.id.action_content_type_sfw to "pref_feed_type_sfw",
                R.id.action_content_type_nsfw to "pref_feed_type_nsfw",
                R.id.action_content_type_nsfl to "pref_feed_type_nsfl")

        if (contentTypes.containsKey(item.itemId)) {
            val newState = !item.isChecked
            settings.edit {
                putBoolean(contentTypes[item.itemId], newState)
            }

            // this applies the new content types and refreshes the menu.
            recheckContentTypes()
            return true
        }

        return OptionMenuHelper.dispatch(this, item) || super.onOptionsItemSelected(item)
    }

    @OnOptionsItemSelected(R.id.action_feedtype)
    fun switchFeedType() {
        var filter = currentFilter
        filter = filter.withFeedType(switchFeedTypeTarget(filter))
        (activity as MainActionHandler).onFeedFilterSelected(filter, initialSearchViewState())
    }

    @OnOptionsItemSelected(R.id.action_refresh)
    fun refreshFeed() {
        resetToolbar()
        loader.reset()
        loader.restart()
    }

    @OnOptionsItemSelected(R.id.action_pin)
    fun pinCurrentFeedFilter() {
        // not bookmarkable anymore.
        onBookmarkableStateChanged(false)

        val filter = currentFilter
        val title = FeedFilterFormatter.format(context, filter).singleline
        (activity as MainActionHandler).pinFeedFilter(filter, title)
    }

    @OnOptionsItemSelected(R.id.action_preload)
    fun preloadCurrentFeed() {
        if (AndroidUtility.isOnMobile(activity)) {
            showDialog(this) {
                content(R.string.preload_not_on_mobile)
                negative()
                positive {
                    doPreloadCurrentFeed(allowOnMobile = true)
                }
            }

            return
        } else {
            doPreloadCurrentFeed(allowOnMobile = false)
        }
    }

    private fun doPreloadCurrentFeed(allowOnMobile: Boolean) {
        val activity = activity ?: return

        // start preloading now
        PreloadService.preload(activity, feed, allowOnMobile)

        Track.preloadCurrentFeed(feed.size)

        if (singleShotService.isFirstTime("preload_info_hint")) {
            showDialog(this) {
                content(R.string.preload_info_hint)
                positive()
            }
        }
    }

    @OnOptionsItemSelected(R.id.action_follow)
    fun onFollowClicked() {
        activeUsername?.let { name ->
            followService.follow(name)
                    .subscribeOn(BackgroundScheduler.instance())
                    .subscribe({}, {})
        }
    }

    @OnOptionsItemSelected(R.id.action_unfollow)
    fun onUnfollowClicked() {
        activeUsername?.let { name ->
            followService.unfollow(name)
                    .subscribeOn(BackgroundScheduler.instance())
                    .subscribe({}, {})
        }
    }

    @OnOptionsItemSelected(R.id.action_block_user)
    fun onBlockUserClicked() {
        activeUsername?.let { name ->
            val dialog = ItemUserAdminDialog.forUser(name)
            dialog.show(fragmentManager, "BlockUserDialog")
        }
    }

    private fun performSearch(query: SearchOptionsView.SearchQuery) {
        hideSearchContainer()

        val current = currentFilter
        var filter = current.withTagsNoReset(query.combined)

        // do nothing, if the filter did not change
        if (equal(current, filter))
            return

        var startAt: ItemWithComment? = null
        if (query.combined.trim().matches("[1-9][0-9]{5,}|id:[0-9]+".toRegex())) {
            filter = filter.withTags("")
            startAt = ItemWithComment(CharMatcher.inRange('0', '9').retainFrom(query.combined).toLong(), null)
        }

        val searchQueryState = searchView.currentState()
        (activity as MainActionHandler).onFeedFilterSelected(filter, searchQueryState, startAt)

        // store the term for later
        if (query.queryTerm.isNotBlank()) {
            recentSearchesServices.storeTerm(query.queryTerm)
        }

        Track.search(query.combined)
    }

    private fun onItemClicked(item: FeedItem, commentId: Long? = null, preview: ImageView? = null) {
        val activity = activity ?: return

        // reset auto open.
        autoOpenOnLoad = null

        val feed = feed

        val idx = feed.indexById(item.id) ?: return

        try {
            val fragment = PostPagerFragment.newInstance(feed, idx, commentId)
            if (preview != null) {
                // pass pixels info to target fragment.
                val image = preview.drawable

                val info = PreviewInfo.of(context, item, image)
                info.preloadFancyPreviewImage(appKodein().instance())
                fragment.setPreviewInfo(info)
            }

            activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.content, fragment)
                    .addToBackStack(null)
                    .commit()

        } catch (error: Exception) {
            logger.warn("Error while showing post", error)
        }

    }

    /**
     * Gets the current filter from this feed.

     * @return The filter this feed uses.
     */
    override val currentFilter: FeedFilter
        get() = feed.filter

    private fun createRecyclerViewClickListener() {
        val listener = RecyclerItemClickListener(recyclerView)

        listener.itemClicked().subscribe { view ->
            extractFeedItemHolder(view)?.let { holder ->
                onItemClicked(holder.item, preview = holder.image)
            }
        }

        listener.itemLongClicked().subscribe { view ->
            extractFeedItemHolder(view)?.let { holder ->
                openQuickPeekDialog(holder.item)
            }
        }

        listener.itemLongClickEnded().subscribe { dismissQuickPeekDialog() }

        settings.changes()
                .startWith("")
                .compose(bindToLifecycle())
                .subscribe { listener.enableLongClick(settings.enableQuickPeek) }
    }

    private fun openQuickPeekDialog(item: FeedItem) {
        dismissQuickPeekDialog()

        // check that the activity is not zero. Might happen, as this method might
        // get called shortly after detaching the activity - which sucks. thanks android.
        val activity = activity
        if (activity != null) {
            quickPeekDialog = PopupPlayerFactory.newInstance(activity, item)
            swipeRefreshLayout.isEnabled = false
        }
    }

    private fun dismissQuickPeekDialog() {
        swipeRefreshLayout.isEnabled = true

        quickPeekDialog?.dismiss()
        quickPeekDialog = null
    }

    private fun refreshRepostInfos(old: Feed, new: Feed) {
        val filter = new.filter
        if (filter.feedType !== FeedType.NEW && filter.feedType !== FeedType.PROMOTED)
            return

        // check if it is possible to get repost info.
        val queryTooLong = (filter.tags ?: "")
                .split("\\s+".toRegex())
                .dropLastWhile { it.isEmpty() }
                .size >= 5

        if (queryTooLong)
            return

        (new - old).minBy { new.feedTypeId(it) }?.let { newestItem ->
            // load repost info for the new items.
            val queryTerm = filter.tags?.let { "$it repost" } ?: "repost"
            val query = FeedService.FeedQuery(
                    filter.withTags(queryTerm),
                    selectedContentType, null, newestItem.id, null)

            refreshRepostsCache(feedService, inMemoryCacheService, query)
                    .observeOn(mainThread())
                    .compose(bindToLifecycle())
                    .subscribe({ feedAdapter.notifyDataSetChanged() }, {})
        }
    }

    private fun onFeedError(error: Throwable) {
        logger.error("Error loading the feed", error)

        when {
            error is FeedException.InvalidContentTypeException -> showInvalidContentTypeError(error)

            error is FeedException.NotPublicException -> showFeedNotPublicError()

            error is FeedException.NotFoundException -> showFeedNotFoundError()

            error is JsonEncodingException -> {
                ErrorDialogFragment.showErrorString(fragmentManager,
                        getString(R.string.could_not_load_feed_json))
            }

            Throwables.getRootCause(error) is ConnectException -> {
                ErrorDialogFragment.showErrorString(fragmentManager,
                        getString(R.string.could_not_load_feed_https))
            }

            else -> ErrorDialogFragment.defaultOnError().call(error)
        }
    }

    private fun showFeedNotFoundError() {
        showDialog(context) {
            content(R.string.error_feed_not_found)
            positive {
                // open top instead
                autoOpenOnLoad = null
                replaceFeedFilter(FeedFilter(), selectedContentType)
            }
        }
    }

    private fun showFeedNotPublicError() {
        val username = currentFilter.likes ?: "???"

        val targetItem = autoOpenOnLoad

        if (targetItem != null) {
            showDialog(this) {
                content(R.string.error_feed_not_public__item, username)

                negative()

                positive {
                    val filter = currentFilter.basic()
                    replaceFeedFilter(filter, selectedContentType, targetItem.itemId)
                }
            }
        } else {
            showDialog(this) {
                content(R.string.error_feed_not_public__general, username)
                positive()
            }
        }
    }

    private fun showInvalidContentTypeError(error: FeedException.InvalidContentTypeException) {
        val requiredType = error.requiredType

        val msg = getString(R.string.could_not_load_feed_content_type, requiredType.name)

        if (userService.isAuthorized) {
            showDialog(this) {
                content(msg + "\n" + getString(R.string.could_not_load_feed_content_type__change, requiredType.name))

                negative()

                positive {
                    val key = when (requiredType) {
                        NSFW -> "pref_feed_type_nsfw"
                        NSFL -> "pref_feed_type_nsfl"
                        else -> "pref_feed_type_sfw"
                    }

                    settings.edit {
                        putBoolean(key, true)
                    }

                    val newContentType = selectedContentType + requiredType
                    replaceFeedFilter(currentFilter, newContentType)
                }
            }
        } else {
            showDialog(context) {
                content(msg + "\n" + getString(R.string.could_not_load_feed_content_type__signin, requiredType.name))
                positive()
            }
        }
    }

    private fun showNoResultsTextView(visible: Boolean) {
        noResultsView.visible = visible
    }

    private fun showErrorLoadingFeedView(visible: Boolean) {
        errorLoadingFeedView.visible = visible
    }

    @OnOptionsItemSelected(R.id.action_search)
    fun resetAndShowSearchContainer() {
        searchView.applyState(initialSearchViewState())
        showSearchContainer(true)
    }

    private fun showSearchContainer(animated: Boolean) {
        if (searchContainerIsVisible())
            return

        val view = view ?: return

        view.post { this.hideToolbar() }

        // prepare search view
        val typeName = FeedFilterFormatter.feedTypeToString(context, currentFilter.withTagsNoReset("dummy"))
        searchView.queryHint = getString(R.string.action_search, typeName)
        searchView.setPadding(0, AndroidUtility.getStatusBarHeight(context), 0, 0)

        searchContainer.visibility = View.VISIBLE

        if (animated) {
            searchContainer.alpha = 0f

            searchContainer.animate()
                    .setListener(endAction(searchView) { it.requestSearchFocus() })
                    .alpha(1f)

            searchView.translationY = (-(0.1 * view.height).toInt()).toFloat()

            searchView.animate()
                    .setInterpolator(DecelerateInterpolator())
                    .translationY(0f)
        } else {
            searchContainer.animate().cancel()
            searchContainer.alpha = 1f

            searchView.animate().cancel()
            searchView.translationY = 0f
        }
    }

    override fun onBackButton(): Boolean {
        if (searchContainerIsVisible()) {
            hideSearchContainer()
            return true
        }

        return false
    }

    private fun searchContainerIsVisible(): Boolean {
        return view != null && searchContainer.visible
    }

    private fun hideSearchContainer() {
        if (!searchContainerIsVisible())
            return

        searchContainer.animate()
                .setListener(hideViewEndAction(searchContainer))
                .alpha(0f)

        val height = view?.height ?: 0
        searchView.animate().translationY((-(0.1 * height).toInt()).toFloat())

        resetToolbar()

        AndroidUtility.hideSoftKeyboard(searchView)
    }

    private fun performAutoOpen() {
        autoOpenOnLoad?.let { autoLoad ->
            logger.info("Trying to do auto load of {}", autoLoad)

            feed.indexById(autoLoad.itemId)?.let { idx ->
                logger.debug("Found item at idx={}", idx)

                // scroll to item now and click
                onItemClicked(feed[idx], autoLoad.commentId)
            }
        }

        autoScrollOnLoad?.let { autoScroll ->
            logger.info("Trying to do auto scroll to {}", autoScroll)
            scrollToItem(autoScroll)
        }

        autoScrollOnLoad = null
    }

    private fun scrollToItem(itemId: Long) {
        val idx = feedAdapter.latestEntries
                .indexOfFirst { it is FeedAdapter.Entry.Item && it.item.id == itemId }
                .takeIf { it >= 0 } ?: return

        logger.debug("Found item at idx={}", idx)

        // over scroll a bit
        recyclerView.scrollToPosition(idx + thumbnailColumCount)
    }

    private fun isSelfInfo(info: Api.Info): Boolean {
        return info.user.name.equals(userService.name, ignoreCase = true)
    }

    private val onScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
            if (scrollToolbar && activity is ToolbarActivity) {
                val activity = activity as ToolbarActivity
                activity.scrollHideToolbarListener.onScrolled(dy)
            }

            if (loader.isLoading)
                return

            val layoutManager = recyclerView?.layoutManager
            if (layoutManager is GridLayoutManager) {
                val totalItemCount = layoutManager.itemCount

                if (dy > 0 && !feed.isAtEnd) {
                    val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                    if (totalItemCount > 12 && lastVisibleItem >= totalItemCount - 12) {
                        logger.info("Request next page now (last visible is {} of {}", lastVisibleItem, totalItemCount)
                        loader.next()
                    }
                }

                if (dy < 0 && !feed.isAtStart) {
                    val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
                    if (totalItemCount > 12 && firstVisibleItem < 12) {
                        logger.info("Request previous page now (first visible is {} of {})", firstVisibleItem, totalItemCount)
                        loader.previous()
                    }
                }
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                if (activity is ToolbarActivity) {
                    val y = ScrollHideToolbarListener.estimateRecyclerViewScrollY(recyclerView)
                            ?: Integer.MAX_VALUE

                    val activity = activity as ToolbarActivity
                    activity.scrollHideToolbarListener.onScrollFinished(y)
                }
            }
        }
    }

    companion object {
        internal val logger = LoggerFactory.getLogger("FeedFragment")

        private const val ARG_FEED = "FeedFragment.feed"
        private const val ARG_FEED_FILTER = "FeedFragment.filter"
        private const val ARG_FEED_START = "FeedFragment.start"
        private const val ARG_FEED_SCROLL = "FeedFragment.scroll"
        private const val ARG_NORMAL_MODE = "FeedFragment.simpleMode"
        private const val ARG_SEARCH_QUERY_STATE = "FeedFragment.searchQueryState"

        /**
         * Creates a new [FeedFragment] for the given feed type.

         * @param feedFilter A query to use for getting data
         * *
         * @return The type new fragment that can be shown now.
         */
        fun newInstance(feedFilter: FeedFilter,
                        start: ItemWithComment?,
                        searchQueryState: Bundle?): FeedFragment {

            val arguments = newArguments(feedFilter, true, start, searchQueryState)

            val fragment = FeedFragment()
            fragment.arguments = arguments
            return fragment
        }

        fun newArguments(feedFilter: FeedFilter, normalMode: Boolean,
                         start: ItemWithComment?,
                         searchQueryState: Bundle?): Bundle {

            return bundle {
                putParcelable(ARG_FEED_FILTER, feedFilter)
                putParcelable(ARG_FEED_START, start)
                putBoolean(ARG_NORMAL_MODE, normalMode)
                putBundle(ARG_SEARCH_QUERY_STATE, searchQueryState)
            }
        }

        private fun extractFeedItemHolder(view: View): FeedItemViewHolder? {
            return view.tag as? FeedItemViewHolder?
        }

    }

    private fun refreshRepostsCache(
            feedService: FeedService, cacheService: InMemoryCacheService, query: FeedService.FeedQuery): Observable<List<Long>> {

        val subject = PublishSubject.create<List<Long>>()

        // refresh happens completely in background to let the query run even if the
        // fragments lifecycle is already destroyed.
        feedService.load(query)
                .subscribeOn(BackgroundScheduler.instance())
                .doAfterTerminate { subject.onCompleted() }
                .subscribe({ items ->
                    if (items.items.isNotEmpty()) {
                        val ids = items.items.map { it.id }
                        cacheService.cacheReposts(ids)
                        subject.onNext(ids)
                        subject.onCompleted()
                    }
                }, {})

        return subject
    }
}
