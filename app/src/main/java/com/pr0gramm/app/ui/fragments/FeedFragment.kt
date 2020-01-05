package com.pr0gramm.app.ui.fragments

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.ScrollView
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.pr0gramm.app.*
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.MessageConverter
import com.pr0gramm.app.feed.*
import com.pr0gramm.app.feed.ContentType.*
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.parcel.getFreezable
import com.pr0gramm.app.parcel.putFreezable
import com.pr0gramm.app.services.*
import com.pr0gramm.app.services.preloading.PreloadManager
import com.pr0gramm.app.services.preloading.PreloadService
import com.pr0gramm.app.ui.*
import com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity
import com.pr0gramm.app.ui.back.BackAwareFragment
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindView
import com.pr0gramm.app.ui.base.launchIgnoreErrors
import com.pr0gramm.app.ui.dialogs.PopupPlayer
import com.pr0gramm.app.ui.views.CustomSwipeRefreshLayout
import com.pr0gramm.app.ui.views.SearchOptionsView
import com.pr0gramm.app.ui.views.UserInfoView
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.instance
import com.squareup.moshi.JsonEncodingException
import com.trello.rxlifecycle.android.FragmentEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import rx.Observable
import rx.subjects.PublishSubject
import java.net.ConnectException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 */
class FeedFragment : BaseFragment("FeedFragment"), FilterFragment, TitleFragment, BackAwareFragment {
    private val settings = Settings.get()

    private val feedService: FeedService by instance()
    private val seenService: SeenService by instance()
    private val bookmarkService: BookmarkService by instance()
    private val userService: UserService by instance()
    private val singleShotService: SingleShotService by instance()
    private val inMemoryCacheService: InMemoryCacheService by instance()
    private val preloadManager: PreloadManager by instance()
    private val inboxService: InboxService by instance()
    private val recentSearchesServices: RecentSearchesServices by instance()
    private val followService: FollowService by instance()
    private val adService: AdService by instance()
    private val shareService: ShareService by instance()

    private val recyclerView: RecyclerView by bindView(R.id.list)
    private val swipeRefreshLayout: CustomSwipeRefreshLayout by bindView(R.id.refresh)
    private val searchContainer: ScrollView by bindView(R.id.search_container)
    private val searchView: SearchOptionsView by bindView(R.id.search_options)

    private val filterArgument: FeedFilter by lazy { arguments?.getFreezable(ARG_FEED_FILTER, FeedFilter)!! }
    private val isNormalMode: Boolean by fragmentArgumentWithDefault(true, ARG_NORMAL_MODE)

    private val doIfAuthorizedHelper = LoginActivity.helper(this)

    private val requestLoadFeedSubject = PublishSubject.create<() -> Unit>()

    private var bookmarkable: Boolean = false
    private var autoScrollRef: ScrollRef? = null

    private var lastCheckForNewItemsTime = Instant(0)

    private lateinit var loader: FeedManager

    private val feedAdapter by lazy {
        val mainActivity = requireActivity() as MainActivity
        FeedAdapter(mainActivity.adViewAdapter)
    }

    private val activeUsername: String? get() = state.userInfo?.info?.user?.name

    private val activeUserId: Int? get() = state.userInfo?.info?.user?.id

    private var scrollToolbar: Boolean = false

    private val actionHandler: MainActionHandler get() = activity as MainActionHandler

    private data class State(
            val feed: Feed = Feed(),
            val preloadedItemIds: LongSparseArray<PreloadManager.PreloadItem> = LongSparseArray(initialCapacity = 0),
            val ownUsername: String? = null,
            val userInfo: UserInfo? = null,
            val adsVisible: Boolean = false,
            val markItemsAsSeen: Boolean = false,
            val userInfoCommentsOpen: Boolean = false,
            val repostRefreshTime: Long = 0,
            val empty: Boolean = false,
            val loading: FeedManager.LoadingSpace? = null,
            val error: String? = null)

    private val feed: Feed get() = state.feed

    private var state: State by observeChange(State()) {
        updateAdapterState()
        stateCh.offer(state)
    }

    private var stateTransaction = StateTransaction({ state }, { updateAdapterState() })

    private val stateCh = ConflatedBroadcastChannel<State>()

    private var followState: FollowState? by observeChange(null) {
        activity?.invalidateOptionsMenu()
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
            val start = arguments?.getParcelable<CommentRef?>(ARG_FEED_START)
            if (start != null) {
                logger.debug { "Requested to open item $start on load" }
                autoScrollRef = ScrollRef(start, autoOpen = true)
            }
        }

        this.scrollToolbar = isNormalMode

        val previousFeed = savedInstanceState?.getExternalValue(requireContext(), ARG_FEED, Feed.FeedParcel)?.feed
        val feed = previousFeed ?: Feed(filterArgument, selectedContentType)
        loader = FeedManager(feedService, feed)

        if (previousFeed == null) {
            loader.restart(around = autoScrollRef?.ref?.itemId)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        return inflater.inflate(R.layout.fragment_feed, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = stateTransaction {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity()

        // prepare the list of items
        val spanCount = thumbnailColumCount
        recyclerView.itemAnimator = null
        recyclerView.adapter = feedAdapter
        recyclerView.layoutManager = InternalGridLayoutManager(activity, spanCount).apply {
            spanSizeLookup = feedAdapter.SpanSizeLookup(spanCount)
        }

        if (activity is RecyclerViewPoolProvider) {
            activity.configureRecyclerView("Feed", recyclerView)
        }

        recyclerView.addOnScrollListener(onScrollListener)

        // we can still swipe up if we are not at the start of the feed.
        swipeRefreshLayout.canScrollUpTest = { !state.empty && state.error == null && !feed.isAtStart }

        swipeRefreshLayout.setColorSchemeResources(ThemeHelper.accentColor)

        swipeRefreshLayout.setOnRefreshListener {
            logger.debug { "onRefresh called for swipe view." }
            if (feed.isAtStart || feed.isEmpty()) {
                refreshFeed()
            } else {
                // do not refresh
                swipeRefreshLayout.isRefreshing = false
            }
        }

        // apply insets if any
        (activity as? ToolbarActivity)?.let { toolbarActivity ->
            toolbarActivity.rxWindowInsets.bindToLifecycle().subscribe { insets ->
                recyclerView.clipToPadding = false

                val top = if (useToolbarTopMargin()) insets.top else 0

                recyclerView.updatePadding(top = top, bottom = insets.bottom)


                if (useToolbarTopMargin()) {
                    // use height of the toolbar to configure swipe refresh layout.
                    val abHeight = AndroidUtility.getActionBarContentOffset(activity)
                    val offset = insets.top
                    swipeRefreshLayout.setProgressViewOffset(false, offset, (offset + 1.5 * (abHeight - offset)).toInt())
                }
            }
        }

        resetToolbar()

        createRecyclerViewClickListener()

        // execute a search when we get a search term
        searchView.searchQuery().bindToLifecycle().subscribe { this.performSearch(it) }
        searchView.searchCanceled().bindToLifecycle().subscribe { hideSearchContainer() }

        // restore open search
        if (savedInstanceState != null && savedInstanceState.getBoolean("searchContainerVisible")) {
            showSearchContainer(false)
        }

        // close search on click into the darkened area.
        searchContainer.setOnTouchListener(DetectTapTouchListener { hideSearchContainer() })

        // lets start receiving feed updates
        subscribeToFeedUpdates()

        launchIgnoreErrors { queryForUserInfo() }

        // start showing ads.
        adService.enabledForType(Config.AdType.FEED)
                .bindToLifecycle()
                .subscribe { show ->
                    trace { "enableAds($show)" }
                    state = state.copy(adsVisible = show)
                }
    }

    private fun subscribeToFeedUpdates() {
        var lastLoadingSpace: FeedManager.LoadingSpace? = null

        loader.updates.bindToLifecycle().subscribe { update ->
            trace { "gotFeedUpdate($update)" }

            when (update) {
                is FeedManager.Update.NewFeed -> {
                    refreshRepostInfos(feed, update.feed)

                    if (lastLoadingSpace == FeedManager.LoadingSpace.PREV) {
                        // scroll down a bit so the new items can be inserted
                        // without scrolling to the to of those new items.
                        feed.getOrNull(0)?.let { item ->
                            autoScrollRef = ScrollRef(CommentRef(item), smoothScroll = false)
                        }
                    }

                    state = state.copy(
                            feed = update.feed,
                            empty = update.remote && update.feed.isEmpty(),
                            error = null, loading = null)
                }

                is FeedManager.Update.Error -> {
                    onFeedError(update.err)
                }

                is FeedManager.Update.LoadingStarted -> {
                    lastLoadingSpace = update.where

                    if (!swipeRefreshLayout.isRefreshing) {
                        state = state.copy(loading = update.where)
                    }
                }

                FeedManager.Update.LoadingStopped -> {
                    swipeRefreshLayout.isRefreshing = false
                    state = state.copy(loading = null)
                }
            }
        }
    }

    private fun updateAdapterState() {
        trace { "updateAdapterState()" }
        checkMainThread()

        val context = context
        if (this.activity == null || context == null) {
            logger.warn { "updateAdapterState called with activity alredy null." }
            return
        }

        val state = this.state

        val filter = state.feed.filter

        val entries = mutableListOf<FeedAdapter.Entry>()

        logger.time("Update adapter") {
            // add a little spacer to the top to account for the action bar
            if (useToolbarTopMargin()) {
                val offset = AndroidUtility.getActionBarHeight(context)
                if (offset > 0) {
                    entries += FeedAdapter.Entry.Spacer(1, height = offset)
                }
            }

            if (state.loading == FeedManager.LoadingSpace.PREV) {
                entries += FeedAdapter.Entry.LoadingHint
            }

            if (state.userInfo != null) {
                val userInfo = state.userInfo
                val isSelfInfo = isSelfInfo(userInfo.info)

                // if we found this user using a normal 'search', we will show a hint
                // that the user exists
                if (filter.tags != null) {
                    if (!isSelfInfo) {
                        val userAndMark = userInfo.info.user.run { UserAndMark(name, mark) }
                        entries += FeedAdapter.Entry.UserHint(userAndMark, this::openUserUploads)
                    }

                } else {
                    entries += FeedAdapter.Entry.User(state.userInfo, isSelfInfo, userActionListener)

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
                val item = state.feed.firstOrNull { it.user.equals(filter.username, ignoreCase = true) }
                if (item != null) {
                    val user = UserAndMark(item.user, item.mark)
                    entries += FeedAdapter.Entry.UserLoading(user)
                    entries += FeedAdapter.Entry.Spacer(2, layout = R.layout.user_info_footer)
                }
            }

            if (!state.userInfoCommentsOpen) {
                // check if we need to check if the posts are 'seen'
                val markAsSeen = state.markItemsAsSeen && !(
                        state.ownUsername != null && state.ownUsername.equals(filter.likes
                                ?: filter.username, ignoreCase = true))

                val adsVisible = state.adsVisible

                // always show at least one ad banner - e.g. during load
                if (adsVisible && state.feed.isEmpty()) {
                    entries += FeedAdapter.Entry.Ad(0)
                }

                for ((idx, item) in state.feed.withIndex()) {
                    val id = item.id
                    val seen = markAsSeen && seenService.isSeen(id)
                    val repost = inMemoryCacheService.isRepost(id)
                    val preloaded = id in state.preloadedItemIds

                    // show an ad banner every ~50 lines
                    if (adsVisible && (idx % (50 * thumbnailColumCount)) == 0) {
                        entries += FeedAdapter.Entry.Ad(idx.toLong())
                    }

                    entries += FeedAdapter.Entry.Item(item, repost, preloaded, seen)
                }

                when {
                    state.loading == FeedManager.LoadingSpace.NEXT ->
                        entries += FeedAdapter.Entry.LoadingHint

                    state.error != null ->
                        entries += FeedAdapter.Entry.Error(state.error)

                    state.empty ->
                        entries += FeedAdapter.Entry.EmptyHint
                }
            }

            if (entries == feedAdapter.latestEntries) {
                logger.debug { "Skip submit of feed items, no change in state." }
                return@time
            }

            val ref = autoScrollRef

            val forceSyncUpdate = ref != null
            feedAdapter.submitList(entries, forceSyncUpdate)

            if (ref?.autoOpen == true) {
                performAutoOpen(ref.ref)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (view != null) {
            val lastVisibleItem = findLastVisibleFeedItem()
            val lastVisibleIndex = lastVisibleItem?.let { feed.indexById(it.id) }

            if (lastVisibleItem != null && lastVisibleIndex != null) {
                outState.putExternalValue(requireContext(), ARG_FEED, feed.parcelAll())
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

    private suspend fun queryForUserInfo() {
        if (!isNormalMode) {
            return
        }

        val userInfo = queryUserInfo()

        state = state.copy(userInfo = userInfo)
        activity?.invalidateOptionsMenu()
    }

    private fun useToolbarTopMargin(): Boolean {
        return isNormalMode
    }

    private val userActionListener = object : UserInfoView.UserActionListener {
        override fun onWriteMessageClicked(name: String) {
            doIfAuthorizedHelper.run {
                ConversationActivity.start(requireContext(), name)
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
            if (userService.isAuthorized) {
                state = state.copy(userInfoCommentsOpen = true)
            }
        }

        override fun shareUserProfile(name: String) {
            shareService.shareUserProfile(requireActivity(), name)
        }
    }

    private fun openUserUploads(name: String) {
        actionHandler.onFeedFilterSelected(currentFilter.basic()
                .withFeedType(FeedType.NEW)
                .withUser(name))
    }

    private suspend fun queryUserInfo(): UserInfo? {
        val filter = filterArgument

        val queryString = filter.username ?: filter.tags ?: filter.likes

        if (queryString != null && queryString.matches("[A-Za-z0-9_]{2,}".toRegex())) {
            val contentTypes = selectedContentType

            val cached = inMemoryCacheService.getUserInfo(contentTypes, queryString)
            if (cached != null) {
                return cached
            }

            // fan out
            val first = async { userService.info(queryString, contentTypes) }
            val second = async { inboxService.getUserComments(queryString, contentTypes) }

            // and wait for responses with defaults
            val info = runCatching { first.await() }.getOrNull() ?: return null
            val comments = runCatching { second.await().comments }.getOrElse { listOf() }

            val userInfo = UserInfo(info, comments)
            inMemoryCacheService.cacheUserInfo(contentTypes, userInfo)

            return userInfo

        } else {
            return null
        }
    }

    override fun onDestroyView() {
        recyclerView.removeOnScrollListener(onScrollListener)

        // destroy any ad views that might still exist
        // feedAdapter.destroyAdView()

        super.onDestroyView()
    }

    private fun resetToolbar() {
        val activity = activity
        if (activity is ToolbarActivity) {
            activity.scrollHideToolbarListener.reset()
        }
    }

    private fun hideToolbar() {
        if (isNormalMode) {
            val activity = activity
            if (activity is ToolbarActivity) {
                activity.scrollHideToolbarListener.hide()
            }
        }
    }

    private fun onBookmarkableStateChanged(bookmarkable: Boolean) {
        if (this.bookmarkable != bookmarkable) {
            this.bookmarkable = bookmarkable
            activity?.invalidateOptionsMenu()
        }
    }

    private val selectedContentType: EnumSet<ContentType>
        get() {
            if (!userService.isAuthorized)
                return EnumSet.of(SFW)

            return settings.contentType
        }

    override suspend fun onResumeImpl(): Unit {
        stateTransaction {
            Track.openFeed(currentFilter)

            // check if we should show the pin button or not.
            if (settings.showPinButton) {
                val bookmarkable = bookmarkService.isBookmarkable(currentFilter)
                onBookmarkableStateChanged(bookmarkable)
            }

            recheckContentTypes()

            if (state.markItemsAsSeen != settings.markItemsAsSeen) {
                state = state.copy(markItemsAsSeen = settings.markItemsAsSeen)
            }

            if (state.ownUsername != userService.name) {
                state = state.copy(ownUsername = userService.name)
            }

            // we might want to check for new items on reload, but only once every two minutes.
            val checkForNewItemInterval = Duration.seconds(if (BuildConfig.DEBUG) 5 else 120)
            val threshold = Instant.now().minus(checkForNewItemInterval)
            if (feed.created.isBefore(threshold) && lastCheckForNewItemsTime.isBefore(threshold)) {
                lastCheckForNewItemsTime = Instant.now()
                checkForNewItems()
            }

            // Observe all preloaded items to get them into the cache and to show the
            // correct state in the ui once they are loaded
            preloadManager.items.let { preloadItems ->
                preloadItems
                        .skip(1)
                        .throttleLast(1, TimeUnit.SECONDS)
                        .startWith(preloadItems.first())
                        .observeOnMainThread()
                        .bindToLifecycle()
                        .subscribe { items ->
                            state = state.copy(preloadedItemIds = items)
                        }
            }

            requestLoadFeedSubject
                    .throttleFirst(1, TimeUnit.SECONDS, MainThreadScheduler)
                    .bindToLifecycle()
                    .subscribe { it() }

        }

        launch {
            stateCh.asFlow()
                    .mapNotNull { it.userInfo?.info?.user?.id }
                    .flatMapLatest { userId -> followService.getState(userId.toLong()) }
                    .collect { followState -> this@FeedFragment.followState = followState }
        }
    }

    private fun performAutoScroll() {
        val ref = autoScrollRef ?: return

        if (ref.autoOpen) {
            return
        }

        // if we currently scroll the view, lets just do this later.
        if (recyclerView.isComputingLayout) {
            Observable.just(Unit)
                    .observeOnMainThread()
                    .bindToLifecycle()
                    .subscribe { performAutoScroll() }

            return
        }

        val containsRef = feedAdapter.latestEntries.any { entry ->
            entry is FeedAdapter.Entry.Item && entry.item.id == ref.itemId
        }

        if (containsRef) {
            autoScrollRef = null
            scrollToItem(ref.itemId, ref.smoothScroll)

        } else if (ref.feed != null) {
            // mark the feed as applied
            autoScrollRef = ref.copy(feed = null)

            // apply the updated feed reference
            state = state.copy(feed = feed.mergeIfPossible(ref.feed) ?: ref.feed)
        }
    }

    fun updateFeedItemTarget(feed: Feed, item: FeedItem) {
        logger.info { "Want to resume from $item" }
        autoScrollRef = ScrollRef(CommentRef(item), feed, smoothScroll = true)
    }

    private fun checkForNewItems() {
        if (!feed.isAtStart || feed.filter.feedType == FeedType.RANDOM || feed.isEmpty()) {
            logger.debug { "Not checking for new items as we are not at the beginning of the feed" }
            return
        }

        launch {
            logger.info { "Checking for new items in current feed" }

            val query = FeedService.FeedQuery(feed.filter, feed.contentType)
            catchAll {
                val response = feedService.load(query)

                val previousIds = feed.mapTo(mutableSetOf()) { it.id }
                val itemCount = response.items.count { it.id !in previousIds }
                if (itemCount > 0 && feed.isNotEmpty() && feed.filter == query.filter) {
                    newItemsSnackbar(itemCount)
                }
            }
        }
    }

    private fun newItemsSnackbar(itemCount: Int) {
        val text = when {
            itemCount == 1 -> getString(R.string.hint_new_items_one)
            itemCount <= 16 -> getString(R.string.hint_new_items_some, itemCount)
            else -> getString(R.string.hint_new_items_many)
        }

        val view = view ?: return

        val snackbar = Snackbar.make(view, text, Snackbar.LENGTH_LONG).apply {
            configureNewStyle()
            setAction(R.string.hint_refresh_load) { refreshFeed() }
            show()
        }

        // dismiss once the fragment stops.
        lifecycle().filter { it == FragmentEvent.STOP }.subscribe { snackbar.dismiss() }
    }

    private fun recheckContentTypes() {
        // check if content type has changed, and reload if necessary
        val feedFilter = feed.filter
        val newContentType = selectedContentType
        if (feed.contentType != newContentType) {
            replaceFeedFilter(feedFilter, newContentType)

            launchIgnoreErrors {
                queryForUserInfo()
            }
        }
    }

    private fun replaceFeedFilter(feedFilter: FeedFilter, newContentType: Set<ContentType>, item: Long? = null) {
        val startAtItemId = item
                ?: autoScrollRef?.ref?.itemId
                ?: findLastVisibleFeedItem(newContentType)?.id

        if (autoScrollRef == null) {
            autoScrollRef = startAtItemId?.let { id -> ScrollRef(CommentRef(id)) }
        }

        // set a new adapter if we have a new content type
        // this clears the current feed immediately
        loader.reset(Feed(feedFilter, newContentType))
        loader.restart(around = startAtItemId)

        activity?.invalidateOptionsMenu()
    }

    /**
     * Finds the last item in the feed that is visible and of one of the given content types

     * @param contentType The target-content type.
     */
    private fun findLastVisibleFeedItem(
            contentType: Set<ContentType> = ContentType.AllSet): FeedItem? {

        // if we don't have a view, there wont be a visible item either.
        if (view == null) {
            return null
        }

        val items = feedAdapter.items.takeUnless { it.isEmpty() } ?: return null

        val layoutManager = recyclerView.layoutManager as? GridLayoutManager
        return layoutManager?.let { _ ->
            // if the first row is visible, skip this stuff.
            val firstCompletelyVisible = layoutManager.findFirstCompletelyVisibleItemPosition()
            if (firstCompletelyVisible == 0 || firstCompletelyVisible == RecyclerView.NO_POSITION)
                return null

            val lastCompletelyVisible = layoutManager.findLastCompletelyVisibleItemPosition()
            if (lastCompletelyVisible == RecyclerView.NO_POSITION)
                return null

            val idx = (lastCompletelyVisible).coerceIn(items.indices)
            items.take(idx)
                    .mapNotNull { item -> (item as? FeedAdapter.Entry.Item)?.item }
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
        min((screenWidth / 120.0 + 0.5).toInt(), if (portrait) 5 else 7)
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

        menu.findItem(R.id.action_bookmark)
                ?.isVisible = bookmarkable

        menu.findItem(R.id.action_preload)
                ?.isVisible = feedType.preloadable

        val adminOnUserProfile = userService.userIsAdmin && activeUsername != null
        menu.findItem(R.id.action_block_user)?.isVisible = adminOnUserProfile
        menu.findItem(R.id.action_open_in_admin)?.isVisible = adminOnUserProfile

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

        val bookmark = menu.findItem(R.id.action_bookmark)
        if (bookmark != null && filter.username != null) {
            // never bookmark a user
            bookmark.isVisible = false
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

        return true == when (item.itemId) {
            R.id.action_feedtype -> switchFeedType()
            R.id.action_refresh -> refreshFeedWithIndicator()
            R.id.action_bookmark -> pinCurrentFeedFilter()
            R.id.action_preload -> preloadCurrentFeed()
            R.id.action_block_user -> onBlockUserClicked()
            R.id.action_search -> resetAndShowSearchContainer()
            R.id.action_open_in_admin -> openUserInAdmin()

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openUserInAdmin() {
        val uri = "https://pr0gramm.com/backend/admin/?view=users&action=show&id=$activeUserId"
        BrowserHelper.openCustomTab(requireContext(), Uri.parse(uri), handover = true)
    }

    private fun switchFeedType() {
        var filter = currentFilter
        filter = filter.withFeedType(switchFeedTypeTarget(filter))
        (activity as MainActionHandler).onFeedFilterSelected(filter, initialSearchViewState())
    }

    private fun refreshFeedWithIndicator() {
        swipeRefreshLayout.isRefreshing = true
        refreshFeed()
    }

    private fun refreshFeed() {
        resetToolbar()
        loader.reset()
        loader.restart()
    }

    private fun pinCurrentFeedFilter() {
        // not bookmarkable anymore.
        onBookmarkableStateChanged(false)

        val filter = currentFilter
        val title = FeedFilterFormatter.format(context, filter).singleline
        (activity as MainActionHandler).bookmarkFilter(filter, title)
    }

    private fun preloadCurrentFeed() {
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

        singleShotService.doOnce("preload_info_hint") {
            showDialog(this) {
                content(R.string.preload_info_hint)
                positive()
            }
        }
    }

    private fun onBlockUserClicked() {
        val name = this.activeUsername
        val fm = this.fragmentManager
        if (name != null) {
            val dialog = ItemUserAdminDialog.forUser(name)
            dialog.maybeShow(fragmentManager, "BlockUserDialog")
        }
    }

    private fun performSearch(query: SearchOptionsView.SearchQuery) {
        hideSearchContainer()

        val current = currentFilter
        var filter = current.withTagsNoReset(query.combined)

        // do nothing, if the filter did not change
        if (current == filter)
            return

        var startAt: CommentRef? = null
        if (query.combined.trim().matches("[1-9][0-9]{5,}|id:[0-9]+".toRegex())) {
            filter = filter.withTags("")
            startAt = CommentRef(query.combined.filter { it in '0'..'9' }.toLong())
        }

        val searchQueryState = searchView.currentState()
        (activity as MainActionHandler).onFeedFilterSelected(filter, searchQueryState, startAt)

        // store the term for later
        if (query.queryTerm.isNotBlank()) {
            recentSearchesServices.storeTerm(query.queryTerm)
        }
    }

    private fun onItemClicked(item: FeedItem, commentRef: CommentRef? = null, preview: ImageView? = null) {
        val activity = activity ?: return

        // reset auto open.
        autoScrollRef = null

        val idx = feed.indexById(item.id) ?: return
        trace { "onItemClicked(feedIndex=$idx, id=${item.id})" }

        try {
            val generator: FancyExifThumbnailGenerator by instance()

            val fragment = PostPagerFragment.newInstance(feed, idx, commentRef, title?.title)
            if (preview != null) {
                // pass pixels info to target fragment.
                val image = preview.drawable

                val info = PreviewInfo.of(requireContext(), item, image)
                info.preloadFancyPreviewImage(generator)
                fragment.setPreviewInfo(info)
            }

            // Only set the target fragment if we are using the same fragment manager
            // to replace the current fragment. This is not the case, if we were started
            // from the Favorites page.
            if (fragmentManager === activity.supportFragmentManager) {
                fragment.setTargetFragment(this, 0)
            }

            activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.content, fragment)
                    .addToBackStack(null)
                    .commit()

        } catch (error: Exception) {
            logger.warn("Error while showing post", error)
        }
    }

    override val currentFilter: FeedFilter
        get() = feed.filter

    override val title: TitleFragment.Title?
        get() {
            val context = context ?: return null
            return FeedFilterFormatter.toTitle(context, feed.filter,
                    titleOverride = arguments?.getString(ARG_TITLE))
        }

    private fun createRecyclerViewClickListener() {
        val listener = RecyclerItemClickListener(recyclerView)

        listener.itemClicked.bindToLifecycle().subscribeIgnoreError { view ->
            extractFeedItemHolder(view)?.let { holder ->
                onItemClicked(holder.item, preview = holder.imageView)
            }
        }

        listener.itemLongClicked.bindToLifecycle().subscribeIgnoreError { view ->
            val holder = extractFeedItemHolder(view) ?: return@subscribeIgnoreError
            val activity = this.activity ?: return@subscribeIgnoreError

            if (!this@FeedFragment.isStateSaved) {
                PopupPlayer.open(activity, holder.item)
                swipeRefreshLayout.isEnabled = false
            }
        }

        listener.itemLongClickEnded.bindToLifecycle().subscribeIgnoreError {
            if (!this@FeedFragment.isStateSaved) {
                PopupPlayer.close(activity ?: return@subscribeIgnoreError)
                swipeRefreshLayout.isEnabled = true
            }
        }

        launchIgnoreErrors {
            settings.changes().onStart { emit("") }.collect {
                listener.enableLongClick(settings.enableQuickPeek)
            }
        }
    }

    private fun refreshRepostInfos(old: Feed, new: Feed) {
        trace { "refreshRepostInfos" }

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

        // get the most recent item in the updated feed items
        val newestItem = (new - old).filter { !it.isPinned }.maxBy { new.feedTypeId(it) } ?: return

        // add 'repost' to query
        val queryTerm = Tags.join("! 'repost'", filter.tags)

        // load repost info for the new items, starting at the most recent one
        val query = FeedService.FeedQuery(filter.withTags(queryTerm),
                contentTypes = new.contentType, older = new.feedTypeId(newestItem))

        launch {
            if (inMemoryCacheService.refreshRepostsCache(feedService, query)) {
                logger.debug { "Repost info was refreshed, updating state now" }
                state = state.copy(repostRefreshTime = System.currentTimeMillis())
            }
        }
    }

    private fun onFeedError(error: Throwable) {
        logger.error("Error loading the feed", error)

        when {
            error is FeedException.InvalidContentTypeException -> showInvalidContentTypeError(error)

            error is FeedException.NotPublicException -> showFeedNotPublicError()

            error is FeedException.NotFoundException -> showFeedNotFoundError()

            error is JsonEncodingException -> {
                state = state.copy(error = getString(R.string.could_not_load_feed_json))
            }

            error.rootCause is ConnectException -> {
                state = state.copy(error = getString(R.string.could_not_load_feed_https))
            }

            else -> {
                val text = ErrorFormatting.getFormatter(error).getMessage(requireContext(), error)
                state = state.copy(error = text)
            }
        }
    }

    private fun showFeedNotFoundError() {
        showDialog(context ?: return) {
            content(R.string.error_feed_not_found)
            positive {
                // open top instead
                autoScrollRef = null
                replaceFeedFilter(FeedFilter(), selectedContentType)
            }
        }
    }

    private fun showFeedNotPublicError() {
        val username = currentFilter.likes ?: "???"

        val targetItem = autoScrollRef

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
            showDialog(context ?: return) {
                content(msg + "\n" + getString(R.string.could_not_load_feed_content_type__signin, requiredType.name))
                positive()
            }
        }
    }

    private fun resetAndShowSearchContainer() {
        searchView.applyState(initialSearchViewState())
        showSearchContainer(true)
    }

    private fun showSearchContainer(animated: Boolean) {
        val context = context ?: return

        if (searchContainerIsVisible())
            return

        val view = view ?: return

        view.post { this.hideToolbar() }

        // ensure that the search view is initialized
        searchView.initView()

        // prepare search view
        val typeName = FeedFilterFormatter.feedTypeToString(context, currentFilter.withTagsNoReset("dummy"))
        searchView.setQueryHint(getString(R.string.action_search, typeName))

        val paddingTop = if (isNormalMode) AndroidUtility.getStatusBarHeight(context) else 0
        searchView.setPadding(0, paddingTop, 0, 0)

        searchContainer.visibility = View.VISIBLE

        if (animated) {
            searchContainer.alpha = 0f

            val searchView = searchView
            searchContainer.animate()
                    .withEndAction { searchView.requestSearchFocus() }
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
        return view != null && searchContainer.isVisible
    }

    private fun hideSearchContainer() {
        if (!searchContainerIsVisible())
            return

        val containerView = this.searchContainer
        containerView.animate()
                .withEndAction { containerView.isVisible = false }
                .alpha(0f)

        val height = view?.height ?: 0
        searchView.animate().translationY((-(0.1 * height).toInt()).toFloat())

        resetToolbar()

        AndroidUtility.hideSoftKeyboard(searchView)
    }

    private fun performAutoOpen(ref: CommentRef) {
        if (isStateSaved || view == null)
            return

        logger.info { "Trying to do auto load of $ref" }
        val idx = feed.indexById(ref.itemId) ?: return

        logger.debug { "Found item at idx=$idx" }

        // scroll to item now and click
        scrollToItem(ref.itemId)
        onItemClicked(feed[idx], ref)

        // reset auto open/scroll reference, so we won't scroll again
        resetAutoScroll()

        // prevent flickering of items before executing the child
        // fragment transaction.
        recyclerView.visibility = View.INVISIBLE
    }

    private fun resetAutoScroll() {
        autoScrollRef = null
    }

    private fun scrollToItem(itemId: Long, smoothScroll: Boolean = false) {
        trace { "scrollToItem($itemId, smooth=$smoothScroll)" }

        logger.debug { "Checking if we can scroll to item $itemId" }
        val idx = feedAdapter.latestEntries
                .indexOfFirst { it is FeedAdapter.Entry.Item && it.item.id == itemId }
                .takeIf { it >= 0 } ?: return

        logger.debug { "Found item at idx=$idx, will scroll now (smooth=$smoothScroll)" }

        val recyclerView = recyclerView
        if (smoothScroll) {
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return

            // smooth scroll to the target position
            val context = recyclerView.context
            layoutManager.startSmoothScroll(OverscrollLinearSmoothScroller(context, idx,
                    dontScrollIfVisible = true,
                    offsetTop = AndroidUtility.getActionBarContentOffset(context) + context.dp(32),
                    offsetBottom = context.dp(32)))

        } else {
            // over scroll a bit
            recyclerView.scrollToPosition(idx + thumbnailColumCount)
        }
    }

    private fun isSelfInfo(info: Api.Info): Boolean {
        return info.user.name.equals(userService.name, ignoreCase = true)
    }

    private fun extractFeedItemHolder(view: View): FeedItemViewHolder? {
        return view.tag as? FeedItemViewHolder
    }

    inner class InternalGridLayoutManager(context: Context, spanCount: Int) : GridLayoutManager(context, spanCount) {
        override fun onLayoutCompleted(state: RecyclerView.State?) {
            super.onLayoutCompleted(state)
            performAutoScroll()
        }
    }

    private val onScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            val activity = activity as? ToolbarActivity
            if (scrollToolbar && activity != null) {
                activity.scrollHideToolbarListener.onScrolled(dy)
            }

            if (loader.isLoading)
                return

            val layoutManager = recyclerView.layoutManager
            if (layoutManager is GridLayoutManager) {
                val totalItemCount = layoutManager.itemCount

                if (dy > 0 && !feed.isAtEnd) {
                    val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                            .takeIf { it >= 0 } ?: return

                    if (totalItemCount > 12 && lastVisibleItem >= totalItemCount - 12) {
                        requestLoadFeedSubject.onNext {
                            logger.info { "Request next page now (last visible is $lastVisibleItem of $totalItemCount" }
                            loader.next()
                        }
                    }
                }

                if (dy < 0 && !feed.isAtStart) {
                    val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
                            .takeIf { it >= 0 } ?: return

                    if (totalItemCount > 12 && firstVisibleItem < 12) {
                        requestLoadFeedSubject.onNext {
                            logger.info { "Request previous page now (first visible is $firstVisibleItem of $totalItemCount)" }
                            loader.previous()
                        }
                    }
                }
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                val activity = activity as? ToolbarActivity
                if (activity != null) {
                    val y = ScrollHideToolbarListener.estimateRecyclerViewScrollY(recyclerView)
                            ?: Integer.MAX_VALUE

                    activity.scrollHideToolbarListener.onScrollFinished(y)
                }
            }
        }
    }

    companion object {
        internal val logger = Logger("FeedFragment")

        private const val ARG_FEED = "FeedFragment.feed"
        private const val ARG_FEED_FILTER = "FeedFragment.filter"
        private const val ARG_FEED_START = "FeedFragment.start"
        private const val ARG_NORMAL_MODE = "FeedFragment.simpleMode"
        private const val ARG_TITLE = "FeedFragment.title"
        private const val ARG_SEARCH_QUERY_STATE = "FeedFragment.searchQueryState"

        fun newInstance(feedFilter: FeedFilter,
                        start: CommentRef?,
                        searchQueryState: Bundle?,
                        title: String? = null): FeedFragment {

            return FeedFragment().apply {
                arguments = bundle {
                    putFreezable(ARG_FEED_FILTER, feedFilter)
                    putParcelable(ARG_FEED_START, start)
                    putBoolean(ARG_NORMAL_MODE, true)
                    putBundle(ARG_SEARCH_QUERY_STATE, searchQueryState)

                    title?.let { putString(ARG_TITLE, title) }
                }
            }
        }

        fun newEmbedArguments(filter: FeedFilter) = bundle {
            putFreezable(ARG_FEED_FILTER, filter)
            putBoolean(ARG_NORMAL_MODE, false)
        }
    }

    private data class ScrollRef(val ref: CommentRef, val feed: Feed? = null,
                                 val autoOpen: Boolean = false,
                                 val smoothScroll: Boolean = false) {

        val itemId: Long get() = ref.itemId
    }
}
