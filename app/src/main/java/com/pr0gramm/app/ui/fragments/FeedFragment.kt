package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.ScrollView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding.support.design.widget.dismisses
import com.pr0gramm.app.*
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.MessageConverter
import com.pr0gramm.app.feed.*
import com.pr0gramm.app.feed.ContentType.*
import com.pr0gramm.app.parcel.getFreezable
import com.pr0gramm.app.parcel.getParcelable
import com.pr0gramm.app.parcel.putFreezable
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
import com.pr0gramm.app.util.AndroidUtility.checkMainThread
import com.squareup.moshi.JsonEncodingException
import com.squareup.picasso.Picasso
import com.trello.rxlifecycle.android.FragmentEvent
import org.kodein.di.erased.instance
import rx.Observable
import rx.android.schedulers.AndroidSchedulers.mainThread
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

    private val recyclerView: androidx.recyclerview.widget.RecyclerView by bindView(R.id.list)
    private val swipeRefreshLayout: CustomSwipeRefreshLayout by bindView(R.id.refresh)
    private val noResultsView: View by bindView(R.id.empty)
    private val errorLoadingFeedView: View by bindView(R.id.error)
    private val searchContainer: ScrollView by bindView(R.id.search_container)
    private val searchView: SearchOptionsView by bindView(R.id.search_options)

    private val filterArgument: FeedFilter by lazy { arguments?.getFreezable(ARG_FEED_FILTER, FeedFilter)!! }
    private val isNormalMode: Boolean by fragmentArgumentWithDefault(true, ARG_NORMAL_MODE)

    private var quickPeekDialog: Dialog? = null

    private val doIfAuthorizedHelper = LoginActivity.helper(this)

    private var bookmarkable: Boolean = false
    private var autoScrollRef: ScrollRef? = null

    private var lastCheckForNewItemsTime = Instant(0)

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
            val preloadedItemIds: Set<Long> = emptySet(),
            val ownUsername: String? = null,
            val userInfo: UserInfo? = null,
            val adsVisible: Boolean = false,
            val seenIndicatorStyle: IndicatorStyle = IndicatorStyle.NONE,
            val userInfoCommentsOpen: Boolean = false,
            val repostRefreshTime: Long = 0)

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
    }

    private var state by observeChange(State(feed.items, feed.filter)) {
        updateAdapterState()
    }

    private var stateTransaction = StateTransaction({ state }, { updateAdapterState() })

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
                logger.debug("Requested to open item {} on load", start)
                autoScrollRef = ScrollRef(start, autoOpen = true)
            }
        }

        this.scrollToolbar = useToolbarTopMargin()

        val previousFeed = savedInstanceState?.getParcelable(ARG_FEED, Feed.FeedParcel)?.feed
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
                .observeOnMainThread()
                .compose(bindToLifecycle<String>())
                .filter { name -> name.equals(activeUsername, ignoreCase = true) }
                .subscribe { activity.invalidateOptionsMenu() }

        // execute a search when we get a search term
        searchView.searchQuery().bindToLifecycle().subscribe { this.performSearch(it) }
        searchView.searchCanceled().bindToLifecycle().subscribe { hideSearchContainer() }
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

        if (autoScrollRef?.autoOpen == true) {
            // perform auto open if needed.
//            feedAdapter.updates.startWith(feedAdapter.latestEntries)
//                    .bindToLifecycle()
//                    .subscribe { performAutoOpen() }
        }

        // start showing ads.
        adService.enabledForType(Config.AdType.FEED)
                .bindToLifecycle()
                .subscribe { show ->
                    trace { "enableAds($show)" }
                    state = state.copy(adsVisible = show)
                }
    }

    private fun subscribeToFeedUpdates() {
        loader.updates.bindToLifecycle().subscribe { update ->
            trace { "gotFeedUpdate($update)" }

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

                FeedManager.Update.LoadingStarted -> {
                    swipeRefreshLayout.isRefreshing = true

                    showNoResultsTextView(false)
                    showErrorLoadingFeedView(false)
                }

                FeedManager.Update.LoadingStopped ->
                    swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun updateAdapterState() {
        trace { "updateAdapterState()" }
        checkMainThread()

        val state = this.state

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

                val adsVisible = state.adsVisible

                // always show at least one ad banner - e.g. during load
                if (adsVisible && state.feedItems.isEmpty()) {
                    entries += FeedAdapter.Entry.Ad(0)
                }

                for ((idx, item) in state.feedItems.withIndex()) {
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
            }

            if (entries == feedAdapter.latestEntries) {
                logger.debug("Skip submit of feed items, no change in state.")
                return
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
                outState.putParcelable(ARG_FEED, feed.parcelAll())
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

        queryUserInfo().take(1).bindToLifecycle().ignoreError().subscribe { value ->
            checkMainThread()
            state = state.copy(userInfo = value)
            activity?.invalidateOptionsMenu()
        }
    }

    private fun useToolbarTopMargin(): Boolean {
        return isNormalMode
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
            if (userService.isAuthorized) {
                state = state.copy(userInfoCommentsOpen = true)
            }
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
                    .observeOnMainThread()

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
        if (isNormalMode) {
            val activity = activity
            if (activity is ToolbarActivity) {
                activity.scrollHideToolbarListener.hide()
            }
        }
    }

    private fun onBookmarkableStateChanged(bookmarkable: Boolean) {
        this.bookmarkable = bookmarkable
        activity?.invalidateOptionsMenu()
    }

    private val selectedContentType: EnumSet<ContentType>
        get() {
            if (!userService.isAuthorized)
                return EnumSet.of(SFW)

            return settings.contentType
        }

    override fun onResume(): Unit = stateTransaction {
        super.onResume()

        if (config.trackItemView) {
            Track.screen(activity, "Feed")
        }

        // check if we should show the pin button or not.
        if (settings.showPinButton) {
            bookmarkService.isBookmarkable(currentFilter)
                    .bindToLifecycleAsync()
                    .subscribe({ onBookmarkableStateChanged(it) }, {})
        }

        recheckContentTypes()

        if (state.seenIndicatorStyle !== settings.seenIndicatorStyle) {
            state = state.copy(seenIndicatorStyle = settings.seenIndicatorStyle)
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
        preloadManager.all().share().let { preloadItems ->
            preloadItems
                    .throttleLast(5, TimeUnit.SECONDS, BackgroundScheduler)
                    .startWith(preloadItems.first())
                    .bindToLifecycleAsync()
                    .ignoreError()
                    .subscribe { items ->
                        state = state.copy(preloadedItemIds = items.mapTo(hashSetOf()) { it.itemId })
                    }
        }
    }

    private fun performAutoScroll() {
        val ref = autoScrollRef ?: return

        if (ref.autoOpen) {
            return
        }

        // if we currently scroll the view, lets just do this later.
        if(recyclerView.isComputingLayout) {
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
            this.feed = feed.mergeIfPossible(ref.feed) ?: ref.feed
        }
    }

    fun updateFeedItemTarget(feed: Feed, item: FeedItem) {
        if (settings.feedScrollOnBack) {
            logger.info("Want to resume from {}", item)
            autoScrollRef = ScrollRef(CommentRef(item), feed, smoothScroll = true)
        }
    }

    private fun checkForNewItems() {
        if (!feed.isAtStart || feed.filter.feedType == FeedType.RANDOM || feed.isEmpty()) {
            logger.info("Not checking for new items as we are not at the beginning of the feed")
            return
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
                .bindToLifecycleAsync()
                .filter { it > 0 && feed.isNotEmpty() && feed.filter == query.filter }
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
            queryForUserInfo()
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

        val items = feedAdapter.items.takeUnless { it.isEmpty() } ?: return null

        val layoutManager = recyclerView.layoutManager as? androidx.recyclerview.widget.GridLayoutManager
        return layoutManager?.let { _ ->
            // if the first row is visible, skip this stuff.
            val firstCompletelyVisible = layoutManager.findFirstCompletelyVisibleItemPosition()
            if (firstCompletelyVisible == 0 || firstCompletelyVisible == androidx.recyclerview.widget.RecyclerView.NO_POSITION)
                return null

            val lastCompletelyVisible = layoutManager.findLastCompletelyVisibleItemPosition()
            if (lastCompletelyVisible == androidx.recyclerview.widget.RecyclerView.NO_POSITION)
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

            if (filter.username != null) {
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
            R.id.action_refresh -> refreshFeed()
            R.id.action_pin -> pinCurrentFeedFilter()
            R.id.action_preload -> preloadCurrentFeed()
            R.id.action_follow -> onFollowClicked()
            R.id.action_unfollow -> onUnfollowClicked()
            R.id.action_block_user -> onBlockUserClicked()
            R.id.action_search -> resetAndShowSearchContainer()

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun switchFeedType() {
        var filter = currentFilter
        filter = filter.withFeedType(switchFeedTypeTarget(filter))
        (activity as MainActionHandler).onFeedFilterSelected(filter, initialSearchViewState())
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
        (activity as MainActionHandler).pinFeedFilter(filter, title)
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

        if (singleShotService.isFirstTime("preload_info_hint")) {
            showDialog(this) {
                content(R.string.preload_info_hint)
                positive()
            }
        }
    }

    private fun onFollowClicked() {
        activeUsername?.let { name ->
            followService.follow(name)
                    .subscribeOn(BackgroundScheduler)
                    .subscribe({}, {})
        }
    }

    private fun onUnfollowClicked() {
        activeUsername?.let { name ->
            followService.unfollow(name)
                    .subscribeOn(BackgroundScheduler)
                    .subscribe({}, {})
        }
    }

    private fun onBlockUserClicked() {
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

        Track.search(query.combined)
    }

    private fun onItemClicked(item: FeedItem, commentRef: CommentRef? = null, preview: ImageView? = null) {
        val activity = activity ?: return

        // reset auto open.
        autoScrollRef = null

        val feed = feed

        val idx = feed.indexById(item.id) ?: return
        trace { "onItemClicked(feedIndex=$idx, id=${item.id})" }

        try {
            val generator: FancyExifThumbnailGenerator by instance()

            val fragment = PostPagerFragment.newInstance(feed, idx, commentRef)
            if (preview != null) {
                // pass pixels info to target fragment.
                val image = preview.drawable

                val info = PreviewInfo.of(context, item, image)
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
                onItemClicked(holder.item, preview = holder.imageView)
            }
        }

        listener.itemLongClicked().ignoreError().subscribe { view ->
            extractFeedItemHolder(view)?.let { holder ->
                openQuickPeekDialog(holder.item)
            }
        }

        listener.itemLongClickEnded()
                .ignoreError()
                .subscribe { dismissQuickPeekDialog() }

        settings.changes()
                .bindToLifecycleAsync()
                .startWith("")
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
        val queryTerm = filter.tags?.let { term ->
            if (term.trim().startsWith("?")) {
                val baseQueryTerm = term.trimStart('?', ' ')
                "? ($baseQueryTerm) repost"
            } else {
                "$term repost"
            }
        }

        // load repost info for the new items, starting at the most recent one
        val query = FeedService.FeedQuery(filter.withTags(queryTerm ?: "repost"),
                contentTypes = new.contentType, older = new.feedTypeId(newestItem))

        inMemoryCacheService.refreshRepostsCache(feedService, query)
                .observeOn(mainThread())
                .compose(bindToLifecycle<Any>().forCompletable())
                .doOnCompleted {
                    this@FeedFragment.trace { "repostInfosUpdated" }
                    logger.debug("Repost info was refreshed, updating state now")
                    state = state.copy(repostRefreshTime = System.currentTimeMillis())
                }
                .onErrorComplete()
                .subscribe()
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

            error.rootCause is ConnectException -> {
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

    private fun resetAndShowSearchContainer() {
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

        val paddingTop = if (isNormalMode) AndroidUtility.getStatusBarHeight(context) else 0
        searchView.setPadding(0, paddingTop, 0, 0)

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

    private fun performAutoOpen(ref: CommentRef) {
        logger.info("Trying to do auto load of {}", ref)
        val idx = feed.indexById(ref.itemId) ?: return

        logger.debug("Found item at idx={}", idx)

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

        logger.debug("Checking if we can scroll to item {}", itemId)
        val idx = feedAdapter.latestEntries
                .indexOfFirst { it is FeedAdapter.Entry.Item && it.item.id == itemId }
                .takeIf { it >= 0 } ?: return

        logger.debug("Found item at idx={}, will scroll now (smooth={})", idx, smoothScroll)

        val recyclerView = recyclerView
        if (smoothScroll) {
            val layoutManager = recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                    ?: return

            // smooth scroll to the target position
            val context = recyclerView.context
            layoutManager.startSmoothScroll(OverscrollLinearSmoothScroller(context, idx,
                    dontScrollIfVisible = true,
                    offsetTop = AndroidUtility.getActionBarContentOffset(context) + AndroidUtility.dp(context, 32),
                    offsetBottom = AndroidUtility.dp(context, 32)))

        } else {
            // over scroll a bit
            recyclerView.scrollToPosition(idx + thumbnailColumCount)
        }
    }

    private fun isSelfInfo(info: Api.Info): Boolean {
        return info.user.name.equals(userService.name, ignoreCase = true)
    }

    inner class InternalGridLayoutManager(context: Context, spanCount: Int) : androidx.recyclerview.widget.GridLayoutManager(context, spanCount) {
        override fun onLayoutCompleted(state: androidx.recyclerview.widget.RecyclerView.State?) {
            super.onLayoutCompleted(state)
            performAutoScroll()
        }
    }

    private val onScrollListener = object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
            if (scrollToolbar && activity is ToolbarActivity) {
                val activity = activity as ToolbarActivity
                activity.scrollHideToolbarListener.onScrolled(dy)
            }

            if (loader.isLoading)
                return

            val layoutManager = recyclerView.layoutManager
            if (layoutManager is androidx.recyclerview.widget.GridLayoutManager) {
                val totalItemCount = layoutManager.itemCount

                if (dy > 0 && !feed.isAtEnd) {
                    val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                    if (lastVisibleItem >= 0) {
                        if (totalItemCount > 12 && lastVisibleItem >= totalItemCount - 12) {
                            logger.info("Request next page now (last visible is {} of {}", lastVisibleItem, totalItemCount)
                            loader.next()
                        }
                    }
                }

                if (dy < 0 && !feed.isAtStart) {
                    val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
                    if (firstVisibleItem >= 0) {
                        if (totalItemCount > 12 && firstVisibleItem < 12) {
                            logger.info("Request previous page now (first visible is {} of {})", firstVisibleItem, totalItemCount)
                            loader.previous()
                        }
                    }
                }
            }
        }

        override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
            if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) {
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
        internal val logger = logger("FeedFragment")

        private const val ARG_FEED = "FeedFragment.feed"
        private const val ARG_FEED_FILTER = "FeedFragment.filter"
        private const val ARG_FEED_START = "FeedFragment.start"
        private const val ARG_NORMAL_MODE = "FeedFragment.simpleMode"
        private const val ARG_SEARCH_QUERY_STATE = "FeedFragment.searchQueryState"

        /**
         * Creates a new [FeedFragment] for the given feed type.

         * @param feedFilter A query to use for getting data
         * *
         * @return The type new fragment that can be shown now.
         */
        fun newInstance(feedFilter: FeedFilter,
                        start: CommentRef?,
                        searchQueryState: Bundle?): FeedFragment {

            val arguments = newArguments(feedFilter, true, start, searchQueryState)

            val fragment = FeedFragment()
            fragment.arguments = arguments
            return fragment
        }

        fun newArguments(feedFilter: FeedFilter, normalMode: Boolean,
                         start: CommentRef?,
                         searchQueryState: Bundle?): Bundle {

            return bundle {
                putFreezable(ARG_FEED_FILTER, feedFilter)
                putParcelable(ARG_FEED_START, start)
                putBoolean(ARG_NORMAL_MODE, normalMode)
                putBundle(ARG_SEARCH_QUERY_STATE, searchQueryState)
            }
        }

        private fun extractFeedItemHolder(view: View): FeedItemViewHolder? {
            return view.tag as? FeedItemViewHolder?
        }
    }

    private data class ScrollRef(val ref: CommentRef, val feed: Feed? = null,
                                 val autoOpen: Boolean = false,
                                 val smoothScroll: Boolean = false) {

        val itemId: Long get() = ref.itemId
    }
}
