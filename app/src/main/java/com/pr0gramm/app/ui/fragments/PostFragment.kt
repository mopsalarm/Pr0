package com.pr0gramm.app.ui.fragments

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder.ofFloat
import android.app.Activity
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.*
import android.view.ViewGroup.LayoutParams
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.pr0gramm.app.*
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.db.FollowState
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.feed.FeedService
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.parcel.CommentListParceler
import com.pr0gramm.app.parcel.TagListParceler
import com.pr0gramm.app.parcel.getFreezable
import com.pr0gramm.app.parcel.putFreezable
import com.pr0gramm.app.services.*
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.ui.*
import com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity
import com.pr0gramm.app.ui.back.BackAwareFragment
import com.pr0gramm.app.ui.base.*
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.showErrorString
import com.pr0gramm.app.ui.dialogs.NewTagDialogFragment
import com.pr0gramm.app.ui.views.PostActions
import com.pr0gramm.app.ui.views.viewer.AbstractProgressMediaView
import com.pr0gramm.app.ui.views.viewer.MediaUri
import com.pr0gramm.app.ui.views.viewer.MediaView
import com.pr0gramm.app.ui.views.viewer.MediaView.Config
import com.pr0gramm.app.ui.views.viewer.MediaViews
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.instance
import com.trello.rxlifecycle.android.FragmentEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import rx.Observable.combineLatest
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import java.io.IOException
import java.util.*
import kotlin.math.min

/**
 * This fragment shows the content of one post.
 */
class PostFragment : BaseFragment("PostFragment"), NewTagDialogFragment.OnAddNewTagsListener, TitleFragment, BackAwareFragment {
    /**
     * Returns the feed item that is displayed in this [PostFragment].
     */
    val feedItem: FeedItem by lazy { arguments?.getFreezable(ARG_FEED_ITEM, FeedItem)!! }

    private val doIfAuthorizedHelper = LoginActivity.helper(this)

    private var state by LazyObservableProperty({ FragmentState(feedItem) }) { _, _ -> adapterStateUpdated() }
    private val stateTransaction = StateTransaction({ state }, { adapterStateUpdated() })

    // start with an empty adapter here
    private val commentTreeHelper = PostFragmentCommentTreeHelper()

    private val activeStateSubject = BehaviorSubject.create<Boolean>(false)
    private var scrollHandler: RecyclerView.OnScrollListener = NoopScrollHandler()

    private var fullscreenAnimator: ObjectAnimator? = null
    private var rewindOnNextLoad: Boolean = false

    private val apiCommentsCh = ConflatedBroadcastChannel(listOf<Api.Comment>())
    private val apiTagsCh = ConflatedBroadcastChannel(listOf<Api.Tag>())

    private var commentRef: CommentRef? by optionalFragmentArgument(name = ARG_COMMENT_REF)

    private val settings = Settings.get()
    private val feedService: FeedService by instance()
    private val voteService: VoteService by instance()
    private val seenService: SeenService by instance()
    private val followService: FollowService by instance()
    private val inMemoryCacheService: InMemoryCacheService by instance()
    private val userService: UserService by instance()
    private val downloadService: DownloadService by instance()
    private val configService: ConfigService by instance()
    private val shareService: ShareService by instance()

    private val swipeRefreshLayout: SwipeRefreshLayout? by bindOptionalView(R.id.refresh)
    private val playerContainer: ViewGroup by bindView(R.id.player_container)
    private val recyclerView: StatefulRecyclerView by bindView(R.id.post_content)
    private val voteAnimationIndicator: ImageView by bindView(R.id.vote_indicator)
    private val repostHint: View by bindView(R.id.repost_hint)

    private var viewer: MediaView? = null
    private var latestInsets: CustomWindowInsets = CustomWindowInsets(0, 0)

    override var title: TitleFragment.Title = TitleFragment.Title("pr0gramm")

    override fun onCreate(savedInstanceState: Bundle?): Unit = stateTransaction(StateTransaction.Dispatch.NEVER) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        savedInstanceState?.let { state ->
            val tags = state.getFreezable("PostFragment.tags", TagListParceler)?.tags
            if (tags != null) {
                this.apiTagsCh.offer(tags)
            }

            val comments = state.getFreezable("PostFragment.comments", CommentListParceler)?.comments
            if (comments != null) {
                this.apiCommentsCh.offer(comments)
            }
        }

        // check if we are admin or not
        userService.loginStates.skip(1).observeOnMainThread().bindToLifecycle().subscribe {
            activity?.invalidateOptionsMenu()
        }
    }

    private fun stopMediaOnViewer() {
        viewer?.stopMedia()
    }

    private fun playMediaOnViewer() {
        viewer?.playMedia()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_post, container, false) as ViewGroup
        addWarnOverlayIfNecessary(inflater, view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = stateTransaction(StateTransaction.Dispatch.ALWAYS) {

        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity()

        (activity as ToolbarActivity?)?.scrollHideToolbarListener?.reset()

        val abHeight = AndroidUtility.getActionBarContentOffset(activity)

        // handle swipe to refresh
        swipeRefreshLayout?.setColorSchemeResources(ThemeHelper.accentColor)
        swipeRefreshLayout?.setProgressViewOffset(false, 0, (1.5 * abHeight).toInt())
        swipeRefreshLayout?.setOnRefreshListener {
            if (!isVideoFullScreen) {
                rewindOnNextLoad = true
                loadItemDetails()
            }
        }

        // apply the flag to the view of the fragment.
        // as long as the fragment is visible, the screen stays on.
        view.keepScreenOn = true

        // default to no scrolling
        scrollHandler = NoopScrollHandler()

        // react to scrolling
        scrollHandler = ScrollHandler()

        recyclerView.addOnScrollListener(scrollHandler)

        recyclerView.itemAnimator = null
        recyclerView.layoutManager = recyclerView.LinearLayoutManager(getActivity())
        recyclerView.adapter = PostAdapter()

        if (activity is RecyclerViewPoolProvider) {
            activity.configureRecyclerView("Post", recyclerView)
        }

        logger.time("Initialize media view") {
            initializeMediaView()
        }

        postAdapter.updates
                .observeOnMainThread()
                .bindToLifecycle()
                .subscribe { tryAutoScrollToCommentNow(smoothScroll = false) }

        userService.loginStates
                .observeOnMainThread(firstIsSync = true)
                .bindToLifecycle()
                .distinctUntilChanged { loginState -> loginState.id }
                .subscribe { loginState ->
                    stateTransaction {
                        if (state.commentsVisible != loginState.authorized) {
                            state = state.copy(commentsVisible = loginState.authorized)
                        }

                        commentTreeHelper.userIsAdmin(loginState.admin)
                    }
                }

        val tags = this.apiTagsCh.value.orEmpty()
        val comments = this.apiCommentsCh.value.orEmpty()

        if (comments.isNotEmpty()) {
            // if we have saved comments we need to apply immediately to ensure
            // we can restore scroll position and stuff.
            updateComments(comments, updateSync = true)
        }

        if (tags.isNotEmpty()) {
            updateTags(tags)
        }

        // listen to comment changes
        commentTreeHelper.itemsObservable.bindToLifecycle().subscribe { commentItems ->
            logger.debug { "Got new list of ${commentItems.size} comments" }
            state = state.copy(comments = commentItems, commentsLoading = false)
        }

        // we do this after the first commentTreeHelper callback above
        if (comments.isEmpty() && tags.isEmpty()) {
            val requiresCacheBust = commentRef?.notificationTime?.let { notificationTime ->
                val threshold = Instant.now().minus(Duration.seconds(60))
                notificationTime.isAfter(threshold)
            }

            loadItemDetails(firstLoad = true, bust = requiresCacheBust ?: false)
        }

        launch(start = CoroutineStart.UNDISPATCHED) {
            apiTagsCh.asFlow().collect { tags ->
                hideProgressIfLoop(tags)
                updateTitle(tags)
            }
        }

        // show the repost badge if this is a repost
        repostHint.isVisible = inMemoryCacheService.isRepost(feedItem)

        activeState.bindToLifecycle().subscribe { active ->
            logger.debug { "Switching viewer state to $active" }
            if (active) {
                playMediaOnViewer()
            } else {
                stopMediaOnViewer()
            }

            if (!active) {
                exitFullscreen()
            }
        }

        setupWindowInsets()
    }

    private fun updateTitle(tags: List<Api.Tag>) {
        val exclude = setOf(
                "sfw", "nsfw", "nsfl", "nsfp", "gif", "video", "sound",
                "text", "porn", "richtiges grau", "achtung laut", "repost", "loop")

        // take the best rated tag that is not excluded
        val title = tags.sortedByDescending { it.confidence }.firstOrNull {
            val tag = it.tag.toLowerCase(Locale.GERMANY)
            tag !in exclude && "loop" !in tag
        } ?: return

        // use the tag as the title for this fragment.
        this.title = TitleFragment.Title(title.tag)

        // and ping the activity to update the title
        val mainActivity = activity as? MainActivity
        mainActivity?.updateActionbarTitle()
    }

    private fun setupWindowInsets() {
        val activity = activity as? ToolbarActivity ?: return

        activity.rxWindowInsets.bindToLifecycle().subscribe { insets ->
            recyclerView.clipToPadding = false

            recyclerView.updatePadding(bottom = insets.bottom)

            val abHeight = AndroidUtility.getActionBarHeight(requireActivity())
            viewer?.updatePadding(top = insets.top + abHeight)

            latestInsets = insets
        }
    }

    private val activeState = run {
        val startStopLifecycle = lifecycle().filter { ev ->
            ev == FragmentEvent.START || ev == FragmentEvent.STOP
        }

        // now combine with the activeStateSubject and return a new observable with
        // the "active state".
        val combined = combineLatest(startStopLifecycle, activeStateSubject) { ev, active ->
            active && ev == FragmentEvent.START
        }

        combined.distinctUntilChanged()
    }

    private fun adapterStateUpdated() {
        checkMainThread()

        if (stateTransaction.isActive) {
            return
        }

        val state = this.state

        logger.debug {
            "Applying post fragment state: h=${state.viewerBaseHeight}, " +
                    "tags=${state.tags.size}, tagVotes=${state.tagVotes.size}, " +
                    "comments=${state.comments.size} (${state.comments.hashCode()}), " +
                    "l=${state.commentsLoading}, viewer=${viewer != null}, " +
                    "mcc=${state.mediaControlsContainer != null}"
        }

        val items = mutableListOf<PostAdapter.Item>()

        viewer?.let { viewer ->
            if (state.viewerBaseHeight > 0) {
                items += PostAdapter.Item.PlaceholderItem(state.viewerBaseHeight,
                        viewer, state.mediaControlsContainer)
            }
        }

        val isOurPost = userService.name.equals(state.item.user, ignoreCase = true)
        items += PostAdapter.Item.InfoItem(state.item, state.itemVote, isOurPost, state.followState, actions)

        if (state.item.deleted) {
            items += PostAdapter.Item.PostIsDeletedItem

        } else {
            items += PostAdapter.Item.TagsItem(state.tags, state.tagVotes, actions)
            items += PostAdapter.Item.CommentInputItem(state.item.id, actions = actions)

            if (state.commentsVisible) {
                if (state.commentsLoadError) {
                    items += PostAdapter.Item.LoadErrorItem
                } else {
                    items += state.comments.map { PostAdapter.Item.CommentItem(it, commentTreeHelper) }

                    if (state.commentsLoading && state.comments.isEmpty()) {
                        items += PostAdapter.Item.CommentsLoadingItem
                    }
                }
            } else {
                items += PostAdapter.Item.NoCommentsWithoutAccount
            }
        }

        submitItemsToAdapter(items)
    }

    private fun submitItemsToAdapter(items: MutableList<PostAdapter.Item>) {
        if (view != null) {
            recyclerView.postAdapter?.submitList(items)
        }
    }

    override fun onDestroyView() {
        recyclerView.removeOnScrollListener(scrollHandler)

        activity?.let {
            // restore orientation if the user closes this view
            Screen.unlockOrientation(it)
        }

        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val tags = apiTagsCh.value.orEmpty()
        if (tags.isNotEmpty()) {
            outState.putFreezable("PostFragment.tags", TagListParceler(tags))
        }

        val comments = apiCommentsCh.value.orEmpty()
        if (comments.isNotEmpty()) {
            outState.putFreezable("PostFragment.comments", CommentListParceler(comments))
        }
    }

    private fun addWarnOverlayIfNecessary(inflater: LayoutInflater, view: ViewGroup) {
        // add a view over the main view, if the post is not visible now
        if (userService.isAuthorized && feedItem.contentType !in settings.contentType) {
            val overlay = inflater.inflate(R.layout.warn_post_can_not_be_viewed, view, false)
            view.addView(overlay)

            // link the hide button
            val button = overlay.findViewById<View>(R.id.hide_warning_button)
            button.setOnClickListener { overlay.removeFromParent() }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        doIfAuthorizedHelper.onActivityResult(requestCode, resultCode)

        if (requestCode == RequestCodes.WRITE_COMMENT && resultCode == Activity.RESULT_OK && data != null) {
            onNewComments(WriteMessageActivity.getNewComment(data))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_post, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val config = configService.config()
        val isImage = isStaticImage(feedItem)
        val adminMode = userService.userIsAdmin

        val alive = !feedItem.deleted

        menu.findItem(R.id.action_refresh)
                ?.isVisible = settings.showRefreshButton && !isVideoFullScreen

        menu.findItem(R.id.action_zoom)
                ?.isVisible = !isVideoFullScreen && alive

        menu.findItem(R.id.action_share_image)
                ?.isVisible = alive

        menu.findItem(R.id.action_search_image)
                ?.isVisible = isImage && settings.imageSearchEngine != ShareService.ImageSearchEngine.NONE && alive

        menu.findItem(R.id.action_delete_item)
                ?.isVisible = adminMode && alive

        menu.findItem(R.id.action_tags_details)
                ?.isVisible = adminMode && alive

        menu.findItem(R.id.action_report)
                ?.isVisible = config.reportReasons.isNotEmpty() && userService.isAuthorized && alive
    }

    fun enterFullscreen() {
        val viewer = viewer ?: return
        val activity = activity ?: return

        if (isStaticImage(feedItem)) {
            val intent = ZoomViewActivity.newIntent(activity, feedItem)
            startActivity(intent)

        } else {
            val rotateIfNeeded = settings.rotateInFullscreen
            val params = ViewerFullscreenParameters.forViewer(activity, viewer, rotateIfNeeded)

            viewer.pivotX = params.pivot.x
            viewer.pivotY = params.pivot.y

            fullscreenAnimator = ObjectAnimator.ofPropertyValuesHolder(viewer,
                    ofFloat(View.ROTATION, params.rotation),
                    ofFloat(View.TRANSLATION_Y, params.trY),
                    ofFloat(View.SCALE_X, params.scale),
                    ofFloat(View.SCALE_Y, params.scale)).apply {

                duration = 500
                start()
            }

            repostHint.isVisible = false

            // hide content below
            swipeRefreshLayout?.isVisible = false

            if (activity is ToolbarActivity) {
                // hide the toolbar if required necessary
                activity.scrollHideToolbarListener.hide()
            }

            viewer.clipBounds = null
            viewer.isVisible = true

            activity.invalidateOptionsMenu()

            // forbid orientation changes while in fullscreen
            Screen.lockOrientation(activity)

            // move to fullscreen!?
            AndroidUtility.applyWindowFullscreen(activity, true)

            state.mediaControlsContainer?.let { mcc ->
                mcc.removeFromParent()
                viewer.addView(mcc)
            }

            if (activity is AdControl) {
                activity.showAds(false)
            }
        }
    }

    private fun realignFullScreen() {
        val viewer = viewer ?: return
        val activity = activity ?: return

        val params = ViewerFullscreenParameters.forViewer(activity, viewer, settings.rotateInFullscreen)
        viewer.pivotX = params.pivot.x
        viewer.pivotY = params.pivot.y
        viewer.translationY = params.trY
        viewer.scaleX = params.scale
        viewer.scaleY = params.scale
    }

    fun exitFullscreen() {
        if (!isVideoFullScreen)
            return

        val activity = activity ?: return
        AndroidUtility.applyWindowFullscreen(activity, false)


        fullscreenAnimator?.cancel()
        fullscreenAnimator = null

        swipeRefreshLayout?.isVisible = true

        // reset the values correctly
        viewer?.apply {
            rotation = 0f
            scaleX = 1f
            scaleY = 1f
            translationX = 0f
        }

        // simulate scrolling to fix the clipping and translationY
        simulateScroll()

        // go back to normal!
        activity.invalidateOptionsMenu()

        if (activity is ToolbarActivity) {
            // show the toolbar again
            activity.scrollHideToolbarListener.reset()
        }

        if (activity is AdControl) {
            activity.showAds(true)
        }

        Screen.unlockOrientation(activity)

        // remove view from the player
        state.mediaControlsContainer?.removeFromParent()

        // and tell the adapter to bind it back to the view.
        recyclerView.postAdapter?.let { adapter ->
            val idx = adapter.items.indexOfFirst { it is PostAdapter.Item.PlaceholderItem }
            if (idx >= 0) {
                adapter.notifyItemChanged(idx)
            }
        }
    }

    internal val isVideoFullScreen: Boolean get() = fullscreenAnimator != null

    private fun onHomePressed(): Boolean {
        if (isVideoFullScreen) {
            exitFullscreen()
            return true
        }

        return false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val activity = activity ?: return true

        return true == when (item.itemId) {
            R.id.action_search_image -> shareService.searchImage(activity, feedItem)
            R.id.action_share_post -> shareService.sharePost(activity, feedItem)
            R.id.action_share_direct_link -> shareService.shareDirectLink(activity, feedItem)
            R.id.action_copy_link -> shareService.copyLink(activity, feedItem)
            R.id.action_share_image -> shareImage()
            R.id.action_refresh -> refreshWithIndicator()
            R.id.action_download -> downloadPostMedia()
            R.id.action_delete_item -> showDeleteItemDialog()
            R.id.action_tags_details -> showTagsDetailsDialog()
            R.id.action_report -> showReportDialog()
            R.id.action_zoom -> enterFullscreen()
            MainActivity.ID_FAKE_HOME -> onHomePressed()
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareImage() {
        launchWithErrorHandler(busyIndicator = true) {
            shareService.shareImage(requireActivity(), feedItem)
        }
    }

    private fun refreshWithIndicator() {
        if (swipeRefreshLayout?.isRefreshing == true || isDetached)
            return

        rewindOnNextLoad = true
        swipeRefreshLayout?.isRefreshing = true
        swipeRefreshLayout?.postDelayed({ this.loadItemDetails() }, 500)
    }

    private fun downloadPostMedia() {
        val helper = requireActivity() as PermissionHelperActivity

        helper.requirePermission(WRITE_EXTERNAL_STORAGE) {
            downloadPostWithPermissionGranted()
        }
    }

    private fun downloadPostWithPermissionGranted() {
        val bitmapDrawable = previewInfo.preview as? BitmapDrawable
        val preview = bitmapDrawable?.bitmap ?: previewInfo.fancy?.valueOrNull

        downloadService
                .downloadWithNotification(feedItem, preview)
                .subscribeOn(Schedulers.io())
                .decoupleSubscribe()
                .observeOnMainThread()
                .bindToLifecycle()
                .subscribe({}, { err: Throwable ->
                    if (err is DownloadService.CouldNotCreateDownloadDirectoryException) {
                        showErrorString(fragmentManager, getString(R.string.error_could_not_create_download_directory))
                    } else {
                        AndroidUtility.logToCrashlytics(DownloadException(err))
                    }
                })
    }

    private class DownloadException(cause: Throwable) : Exception(cause)

    override suspend fun onResumeImpl() {
        launch {
            voteService.getVote(feedItem).collect { vote ->
                state = state.copy(itemVote = vote)
            }
        }

        launch {
            apiCommentsCh
                    .asFlow()
                    .flatMapLatest { comments -> voteService.getCommentVotes(comments) }
                    .collect { votes -> commentTreeHelper.updateVotes(votes) }
        }

        launch {
            apiTagsCh
                    .asFlow()
                    .flatMapLatest { tags -> voteService.getTagVotes(tags) }
                    .collect { votes -> state = state.copy(tagVotes = votes) }
        }

        launch {
            followService.isFollowing(feedItem.userId).collect { followState ->
                state = state.copy(followState = followState)
            }
        }

        // observeOnMainThread uses post to scroll in the next frame.
        // this prevents the viewer from getting bad clipping.
        recyclerView.postAdapter?.let { adapter ->
            adapter.updates.skip(1).observeOnMainThread().bindToLifecycle().subscribe {
                simulateScroll()
            }
        }
    }

    /**
     * Loads the information about the post. This includes the
     * tags and the comments.
     */
    private fun loadItemDetails(firstLoad: Boolean = false, bust: Boolean = false) {
        // postDelayed could execute this if it is not added anymore
        if (!isAdded || isDetached) {
            return
        }

        if (feedItem.deleted) {
            // that can be handled quickly...
            swipeRefreshLayout?.isRefreshing = false
            return
        }

        // update state to show "loading" items
        state = state.copy(
                commentsLoading = firstLoad || state.commentsLoadError || apiCommentsCh.value.isEmpty(),
                commentsLoadError = false)

        launchIgnoreErrors {
            try {
                onPostReceived(feedService.post(feedItem.id, bust))
                swipeRefreshLayout?.isRefreshing = false

            } catch (err: Exception) {
                if (err is CancellationException) {
                    return@launchIgnoreErrors
                }

                swipeRefreshLayout?.isRefreshing = false

                if (err.rootCause !is IOException) {
                    AndroidUtility.logToCrashlytics(err)
                }

                stateTransaction {
                    updateComments(emptyList(), updateSync = true)
                    state = state.copy(commentsLoadError = true, commentsLoading = false)
                }
            }
        }
    }

    private fun showDeleteItemDialog() {
        val dialog = ItemUserAdminDialog.forItem(feedItem)
        dialog.maybeShow(fragmentManager)
    }

    private fun showTagsDetailsDialog() {
        val dialog = TagsDetailsDialog.newInstance(feedItem.id)
        dialog.maybeShow(fragmentManager)
    }

    private fun showReportDialog() {
        val dialog = ReportDialog.forItem(feedItem)
        dialog.maybeShow(fragmentManager)
    }

    private fun showPostVoteAnimation(vote: Vote?) {
        if (vote === null || vote === Vote.NEUTRAL)
            return

        // quickly center the vote button
        simulateScroll()

        val voteAnimationIndicator = voteAnimationIndicator

        voteAnimationIndicator.setImageResource(when (vote) {
            Vote.UP -> R.drawable.ic_vote_up
            Vote.DOWN -> R.drawable.ic_vote_down
            else -> R.drawable.ic_vote_fav
        })

        voteAnimationIndicator.visibility = View.VISIBLE
        voteAnimationIndicator.alpha = 0f
        voteAnimationIndicator.scaleX = 0.7f
        voteAnimationIndicator.scaleY = 0.7f

        ObjectAnimator.ofPropertyValuesHolder(voteAnimationIndicator,
                ofFloat(View.ALPHA, 0f, 0.6f, 0.7f, 0.6f, 0f),
                ofFloat(View.SCALE_X, 0.7f, 1.3f),
                ofFloat(View.SCALE_Y, 0.7f, 1.3f)).apply {

            doOnEnd { voteAnimationIndicator.isVisible = false }
            start()
        }
    }

    private fun initializeMediaView() {
        val activity = requireActivity()
        val uri = buildMediaUri()

        val viewerConfig = Config(activity, uri, audio = feedItem.audio, previewInfo = previewInfo)
        val viewer = logger.time("MediaView.newInstance") {
            MediaViews.newInstance(viewerConfig)
        }

        // remember for later
        this.viewer = viewer

        viewer.viewed().subscribe {
            doInBackground { seenService.markAsSeen(feedItem.id) }
            //  mark this item seen. We do that in a background thread
        }

        // inform viewer about fragment lifecycle events!
        MediaViews.adaptFragmentLifecycle(lifecycle(), viewer)

        registerTapListener(viewer)

        // add views in the correct order (normally first child)
        val idx = playerContainer.indexOfChild(voteAnimationIndicator)
        playerContainer.addView(viewer, idx)

        // Add a container for the children
        val mediaControlsContainer = FrameLayout(requireContext())
        mediaControlsContainer.layoutParams = FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)

        // add space to the top of the viewer or to the screen to compensate
        // for the action bar.
        val viewerPaddingTop = AndroidUtility.getActionBarHeight(activity) + latestInsets.top
        viewer.updatePadding(top = viewerPaddingTop)

        if (feedItem.width > 0 && feedItem.height > 0) {

            val toolbarActivity = activity as? ToolbarActivity

            toolbarActivity?.rxWindowInsets?.bindToLifecycle()?.subscribe { insets ->
                val currentViewerPaddingTop = AndroidUtility.getActionBarHeight(activity) + insets.top

                val screenSize = Point().also { activity.windowManager.defaultDisplay.getSize(it) }
                val expectedMediaHeight = screenSize.x * feedItem.height / feedItem.width
                val expectedViewerHeight = expectedMediaHeight + currentViewerPaddingTop
                state = state.copy(viewerBaseHeight = expectedViewerHeight)

                logger.debug { "Initialized viewer height to $expectedViewerHeight" }
            }
        }

        viewer.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val newHeight = viewer.measuredHeight
            if (newHeight != state.viewerBaseHeight) {
                logger.debug { "Change in viewer height detected, setting height to ${state.viewerBaseHeight} to $newHeight" }

                state = state.copy(viewerBaseHeight = newHeight)

                if (isVideoFullScreen) {
                    realignFullScreen()
                }
            }
        }

        state = state.copy(mediaControlsContainer = mediaControlsContainer)


        // add the controls as child of the controls-container.
        viewer.controllerView()
                .compose(bindUntilEvent(FragmentEvent.DESTROY_VIEW))
                .doOnNext { view -> logger.debug { "Adding view $view to placeholder" } }
                .subscribe { mediaControlsContainer.addView(it) }

        // show sfw/nsfw as a little flag, if the user is admin
        if (settings.showContentTypeFlag) {
            // show the little admin triangle
            val size = requireContext().dip2px(16)
            ViewCompat.setBackground(mediaControlsContainer,
                    TriangleDrawable(feedItem.contentType, size))

            mediaControlsContainer.minimumHeight = size
        }
    }

    private fun buildMediaUri(): MediaUri {
        // initialize a new viewer fragment
        val uri = MediaUri.of(requireContext(), feedItem)

        if (!uri.isLocalFile && AndroidUtility.isOnMobile(context)) {
            val confirmAll = settings.confirmPlayOnMobile === Settings.ConfirmOnMobile.ALL
            val confirmVideo = settings.confirmPlayOnMobile === Settings.ConfirmOnMobile.VIDEO
                    && uri.mediaType !== MediaUri.MediaType.IMAGE

            if (confirmAll || confirmVideo) {
                return uri.withDelay(true)
            }
        }

        return uri
    }

    private val previewInfo: PreviewInfo by lazy {
        val parent = parentFragment
        if (parent is PreviewInfoSource) {
            parent.previewInfoFor(feedItem)?.let { return@lazy it }
        }

        return@lazy PreviewInfo.of(requireContext(), feedItem)
    }

    private fun simulateScroll() {
        val handler = scrollHandler
        if (handler is ScrollHandler) {
            handler.onScrolled(this.recyclerView, 0, 0)
        } else {
            // simulate a scroll to "null"
            offsetMediaView(true, 0.0f)
        }
    }

    /**
     * Registers a tap listener on the given viewer instance. The listener is used
     * to handle double-tap-to-vote events from the view.

     * @param viewer The viewer to register the tap listener to.
     */
    private fun registerTapListener(viewer: MediaView) {
        if (feedItem.deleted)
            return

        viewer.tapListener = object : MediaView.TapListener {
            val isImage = isStaticImage(feedItem)

            override fun onSingleTap(event: MotionEvent): Boolean {
                if (isImage && settings.singleTapForFullscreen) {
                    enterFullscreen()
                }

                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                if (settings.doubleTapToUpvote) {
                    launch { doVoteOnDoubleTap() }
                }

                return true
            }
        }
    }

    private suspend fun doVoteOnDoubleTap() {
        val currentVote = voteService.getVote(feedItem).first()
        doVoteFeedItem(currentVote.nextUpVote)
    }

    /**
     * Called with the downloaded post information.

     * @param post The post information that was downloaded.
     */
    private fun onPostReceived(post: Api.Post) {
        stateTransaction {
            // update from post
            updateTags(post.tags)
            updateComments(post.comments)
        }

        if (rewindOnNextLoad) {
            rewindOnNextLoad = false
            viewer?.rewind()
        }
    }

    private fun updateTags(tags_: List<Api.Tag>) {
        // ensure a deterministic ordering for the tasks
        val comparator = compareByDescending<Api.Tag> { it.confidence }.thenBy { it.id }

        val tags = inMemoryCacheService
                .enhanceTags(feedItem.id, tags_)
                .sortedWith(comparator)

        apiTagsCh.offer(tags)

        state = state.copy(tags = tags)
    }

    /**
     * If the current post is a loop, we'll check if it is a loop. If it is,
     * we will hide the little video progress bar.
     */
    private fun hideProgressIfLoop(tags: List<Api.Tag>) {
        val actualView = viewer?.actualMediaView
        if (actualView is AbstractProgressMediaView) {
            if (tags.any { it.isLoopTag() }) {
                actualView.hideVideoProgress()
            }
        }
    }

    private fun updateComments(
            comments: List<Api.Comment>,
            updateSync: Boolean = false,
            extraChanges: (CommentTree.Input) -> CommentTree.Input = { it }): Unit = stateTransaction {

        this.apiCommentsCh.offer(comments.toList())

        // show comments now
        logger.info { "Sending ${comments.size} comments to tree helper" }
        commentTreeHelper.updateComments(comments, updateSync) { state ->
            extraChanges(state.copy(
                    op = feedItem.user,
                    self = userService.name,
                    isAdmin = userService.userIsAdmin))
        }

        // if we dont have any comments, we stop loading now.
        if (comments.isEmpty()) {
            state = state.copy(commentsLoading = false)
        }
    }

    /**
     * Called from the [PostPagerFragment] if this fragment
     * is currently the active/selected fragment - or if it is not the active fragment anymore.

     * @param active The new active status.
     */
    fun setActive(active: Boolean) {
        activeStateSubject.onNext(active)

        if (active) {
            Track.viewItem(feedItem.id)
        }
    }

    override fun onAddNewTags(tags: List<String>) {
        val previousTags = this.apiTagsCh.value.orEmpty()

        // allow op to tag a more restrictive content type.
        val op = feedItem.user.equals(userService.name, true) || userService.userIsAdmin
        val newTags = tags.filter { tag ->
            isValidTag(tag) || (op && isMoreRestrictiveContentTypeTag(previousTags, tag))
        }

        if (newTags.isNotEmpty()) {
            logger.info { "Adding new tags $newTags to post" }

            launchWithErrorHandler(busyIndicator = true) {
                updateTags(withBackgroundContext(NonCancellable) {
                    voteService.tag(feedItem.id, newTags)
                })
            }
        }
    }

    private fun onNewComments(response: Api.NewComment) {
        autoScrollToComment(response.commentId, delayed = true)

        updateComments(response.comments) { state ->
            state.copy(selectedCommentId = response.commentId, baseVotes = state.baseVotes.let { votes ->
                val copy = votes.clone()
                copy.put(response.commentId, Vote.UP)
                copy
            })
        }

        view?.let { fragmentView ->
            Snackbar.make(fragmentView, R.string.comment_written_successful, Snackbar.LENGTH_LONG)
                    .configureNewStyle(activity)
                    .setAction(R.string.okay) {}
                    .show()
        }
    }

    private fun autoScrollToComment(commentId: Long, delayed: Boolean = false, smoothScroll: Boolean = false) {
        commentRef = CommentRef(feedItem.id, commentId)

        if (!delayed) {
            tryAutoScrollToCommentNow(smoothScroll)
        }
    }

    private fun tryAutoScrollToCommentNow(smoothScroll: Boolean) {
        val commentId = commentRef?.commentId ?: return

        // get the current recycler view and adapter.
        val adapter = this.recyclerView.postAdapter ?: return

        val idx = adapter.items.indexOfFirst { item ->
            item is PostAdapter.Item.CommentItem && item.commentTreeItem.commentId == commentId
        }

        if (idx >= 0) {
            if (smoothScroll) {
                val scroller = CenterLinearSmoothScroller(this.recyclerView.context, idx)
                this.recyclerView.layoutManager?.startSmoothScroll(scroller)

            } else {
                this.recyclerView.scrollToPosition(idx)
            }

            commentTreeHelper.selectComment(commentId)
            commentRef = null
        }
    }

    fun mediaHorizontalOffset(offset: Int) {
        viewer?.translationX = offset.toFloat()
    }

    override fun onBackButton(): Boolean {
        if (isVideoFullScreen) {
            exitFullscreen()
            return true
        }

        return false
    }

    private class NoopScrollHandler : RecyclerView.OnScrollListener()

    private inner class ScrollHandler : RecyclerView.OnScrollListener() {
        val fancyScrollVertical = settings.fancyScrollVertical

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (isVideoFullScreen)
                return

            val viewer = viewer ?: return

            // get our facts straight
            val recyclerHeight = recyclerView.height
            val scrollEstimate = ScrollHideToolbarListener.estimateRecyclerViewScrollY(recyclerView)
            var viewerVisible = scrollEstimate != null

            val scrollY = scrollEstimate ?: viewer.height
            val viewerHeight = viewer.height
            val doFancyScroll = viewerHeight < recyclerHeight && fancyScrollVertical

            val toolbar = (activity as ToolbarActivity).scrollHideToolbarListener
            if (!doFancyScroll || dy < 0 || scrollY > toolbar.toolbarHeight) {
                toolbar.onScrolled(dy)
            }

            val scroll = if (doFancyScroll) 0.7f * scrollY else scrollY.toFloat()

            if (doFancyScroll) {
                val clipTop = (scroll + 0.5f).toInt()
                val clipBottom = viewer.height - (scrollY - scroll + 0.5f).toInt()

                if (clipTop < clipBottom) {
                    viewer.clipBounds = Rect(0, clipTop, viewer.right, clipBottom)
                } else {
                    viewerVisible = false
                }
            } else {
                // reset bounds. we might have set some previously and want
                // to clear those bounds now.
                viewer.clipBounds = null
            }

            offsetMediaView(viewerVisible, scroll)

            // position the vote indicator
            val remaining = (viewerHeight - scrollY).toFloat()
            val tbVisibleHeight = toolbar.visibleHeight
            val voteIndicatorY = tbVisibleHeight + min(
                    (remaining - tbVisibleHeight) / 2,
                    ((recyclerHeight - tbVisibleHeight) / 2).toFloat())

            voteAnimationIndicator.translationY = voteIndicatorY
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (!isVideoFullScreen && newState == RecyclerView.SCROLL_STATE_IDLE) {
                val y = ScrollHideToolbarListener.estimateRecyclerViewScrollY(recyclerView)
                        ?: Integer.MAX_VALUE
                (activity as ToolbarActivity).scrollHideToolbarListener.onScrollFinished(y)
            }
        }
    }

    /**
     * Positions the media view using the given offset (on the y axis)
     */
    internal fun offsetMediaView(viewerVisible: Boolean, offset: Float) {
        val viewer = viewer ?: return

        if (viewerVisible) {
            // finally position the viewer
            viewer.translationY = -offset
            viewer.visibility = View.VISIBLE

            // position the repost badge, if it is visible
            if (inMemoryCacheService.isRepost(feedItem)) {
                repostHint.isVisible = true
                repostHint.translationY = viewer.paddingTop.toFloat() - repostHint.pivotY - offset
            }
        } else {
            viewer.isVisible = false
            repostHint.isVisible = false
        }
    }

    /**
     * Returns true, if the given tag looks like some "loop" tag.
     */
    private fun Api.Tag.isLoopTag(): Boolean {
        val lower = tag.toLowerCase(Locale.GERMANY)
        return "loop" in lower && !("verschenkt" in lower || "verkackt" in lower)
    }

    /**
     * Returns true, if the given url links to a static image.
     * This does only a check on the filename and not on the data.

     * @param image The url of the image to check
     */
    private fun isStaticImage(image: FeedItem): Boolean {
        return listOf(".jpg", ".jpeg", ".png").any {
            image.image.endsWith(it, ignoreCase = true)
        }
    }

    private fun doVoteFeedItem(vote: Vote): Boolean {
        return doIfAuthorizedHelper.runAuthWithRetry {
            showPostVoteAnimation(vote)

            launchWithErrorHandler {
                withBackgroundContext(NonCancellable) {
                    voteService.vote(feedItem, vote)
                }
            }
        }
    }

    private val postAdapter: PostAdapter
        get() = recyclerView.postAdapter ?: throw IllegalStateException("no comment adapter set")

    inner class PostFragmentCommentTreeHelper : CommentTreeHelper() {
        override fun onCommentVoteClicked(comment: Api.Comment, vote: Vote): Boolean {
            return runBlocking {
                doIfAuthorizedHelper.run {
                    launchWithErrorHandler {
                        withBackgroundContext(NonCancellable) {
                            voteService.vote(comment, vote)
                        }
                    }

                    true
                }
            }
        }

        override fun onReplyClicked(comment: Api.Comment) {
            val byId = apiCommentsCh.value.associateBy { it.id }

            val parentComments = mutableListOf<WriteMessageActivity.ParentComment>()

            var current: Api.Comment? = comment
            while (current != null) {
                parentComments.add(WriteMessageActivity.ParentComment.ofComment(current))
                current = byId[current.parent]
            }

            doIfAuthorizedHelper.runAuthWithRetry {
                val context = context ?: return@runAuthWithRetry
                startActivityForResult(
                        WriteMessageActivity.answerToComment(context, feedItem, comment, parentComments),
                        RequestCodes.WRITE_COMMENT)

            }
        }

        override fun onCommentAuthorClicked(comment: Api.Comment) {
            (parentFragment as PostPagerFragment).onUsernameClicked(comment.name)
        }

        override fun onCopyCommentLink(comment: Api.Comment) {
            shareService.copyLink(context ?: return, feedItem, comment)
        }

        override fun onDeleteCommentClicked(comment: Api.Comment): Boolean {
            val dialog = ItemUserAdminDialog.forComment(comment.id, comment.name)
            dialog.maybeShow(fragmentManager)
            return true
        }

        override fun onBlockUserClicked(comment: Api.Comment): Boolean {
            val dialog = ItemUserAdminDialog.forUser(comment.name)
            dialog.maybeShow(fragmentManager)
            return true
        }

        override fun onReportCommentClicked(comment: Api.Comment) {
            val dialog = ReportDialog.forComment(feedItem, comment.id)
            dialog.maybeShow(fragmentManager)
        }

        override fun itemClicked(ref: Linkify.Item): Boolean {
            if (ref.item != feedItem.id) {
                return false
            }

            // scroll to the top
            recyclerView.adapter?.itemCount?.takeIf { it > 0 }?.let {
                recyclerView.smoothScrollToPosition(0)
            }

            return true
        }

        override fun commentClicked(ref: Linkify.Comment): Boolean {
            if (ref.item == feedItem.id) {
                val hasComment = postAdapter.items.any { item ->
                    item is PostAdapter.Item.CommentItem && item.commentTreeItem.commentId == ref.comment
                }

                if (hasComment) {
                    autoScrollToComment(ref.comment, smoothScroll = true)
                } else {
                    val rootView = view ?: return false
                    Snackbar.make(rootView, R.string.hint_comment_not_found, Snackbar.LENGTH_SHORT)
                            .configureNewStyle(activity)
                            .setAction(R.string.doh) { }
                            .show()
                }

                return true
            }

            return false
        }
    }

    private val actions = object : PostActions {
        override fun voteTagClicked(tag: Api.Tag, vote: Vote): Boolean {
            return doIfAuthorizedHelper.runAuthWithRetry {
                launchWithErrorHandler {
                    withBackgroundContext(NonCancellable) {
                        voteService.vote(tag, vote)
                    }
                }
            }
        }

        override fun onTagClicked(tag: Api.Tag) {
            (parentFragment as PostPagerFragment).onTagClicked(tag)
        }

        override fun onUserClicked(username: String) {
            (parentFragment as PostPagerFragment).onUsernameClicked(username)
        }

        override fun votePostClicked(vote: Vote): Boolean {
            return doVoteFeedItem(vote)
        }

        override fun writeNewTagClicked() {
            doIfAuthorizedHelper.run {
                if (!childFragmentManager.isStateSaved) {
                    val dialog = NewTagDialogFragment()
                    dialog.show(childFragmentManager, null)
                }
            }
        }

        override fun writeCommentClicked(text: String): Boolean {
            AndroidUtility.hideSoftKeyboard(view)
            return doIfAuthorizedHelper.runAuthWithRetry {
                launchWithErrorHandler(busyIndicator = true) {
                    onNewComments(voteService.postComment(feedItem, 0, text))
                }
            }
        }

        override suspend fun updateFollowUser(follow: FollowAction) {
            doIfAuthorizedHelper.runAuthSuspend {
                followService.update(follow, feedItem.userId, feedItem.user)
            }
        }
    }

    private data class FragmentState(
            val item: FeedItem,
            val itemVote: Vote = Vote.NEUTRAL,
            val tags: List<Api.Tag> = emptyList(),
            val tagVotes: LongSparseArray<Vote> = LongSparseArray(initialCapacity = 0),
            val viewerBaseHeight: Int = 0,
            val comments: List<CommentTree.Item> = emptyList(),
            val commentsVisible: Boolean = true,
            val commentsLoading: Boolean = false,
            val commentsLoadError: Boolean = false,
            val followState: FollowState? = null,
            val mediaControlsContainer: View? = null)

    companion object {
        private const val ARG_FEED_ITEM = "PF.post"
        private const val ARG_COMMENT_REF = "PF.commentRef"

        /**
         * Creates a new instance of a [PostFragment] displaying the
         * given [FeedItem].
         */
        fun newInstance(item: FeedItem, commentRef: CommentRef? = null): PostFragment {
            return PostFragment().arguments {
                putFreezable(ARG_FEED_ITEM, item)
                putParcelable(ARG_COMMENT_REF, commentRef)
            }
        }

        private val RecyclerView.postAdapter: PostAdapter? get() = adapter as? PostAdapter
    }
}
