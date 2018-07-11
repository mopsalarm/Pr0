package com.pr0gramm.app.ui.fragments

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder.ofFloat
import android.app.Activity
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.view.ViewCompat
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.view.ViewGroup.LayoutParams
import android.widget.FrameLayout
import com.google.common.base.Throwables
import com.jakewharton.rxbinding.view.layoutChanges
import com.pr0gramm.app.R
import com.pr0gramm.app.RequestCodes
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.feed.FeedService
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.parcel.CommentListParceler
import com.pr0gramm.app.parcel.TagListParceler
import com.pr0gramm.app.parcel.core.Parceler
import com.pr0gramm.app.services.*
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.ui.*
import com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity
import com.pr0gramm.app.ui.back.BackAwareFragment
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.dialogs.DeleteCommentDialog
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.showErrorString
import com.pr0gramm.app.ui.dialogs.NewTagDialogFragment
import com.pr0gramm.app.ui.dialogs.ignoreError
import com.pr0gramm.app.ui.views.PostActions
import com.pr0gramm.app.ui.views.Pr0grammIconView
import com.pr0gramm.app.ui.views.viewer.AbstractProgressMediaView
import com.pr0gramm.app.ui.views.viewer.MediaUri
import com.pr0gramm.app.ui.views.viewer.MediaView
import com.pr0gramm.app.ui.views.viewer.MediaView.Config
import com.pr0gramm.app.ui.views.viewer.MediaViews
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.AndroidUtility.checkMainThread
import com.trello.rxlifecycle.android.FragmentEvent
import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import org.kodein.di.erased.instance
import rx.Observable
import rx.Observable.combineLatest
import rx.lang.kotlin.subscribeBy
import rx.subjects.BehaviorSubject
import java.io.IOException

/**
 * This fragment shows the content of one post.
 */
class PostFragment : BaseFragment("PostFragment"), NewTagDialogFragment.OnAddNewTagsListener, BackAwareFragment {
    /**
     * Returns the feed item that is displayed in this [PostFragment].
     */
    val feedItem: FeedItem by fragmentArgument(name = ARG_FEED_ITEM)

    private val doIfAuthorizedHelper = LoginActivity.helper(this)

    private var state by LazyObservableProperty({ FragmentState(feedItem) }) { _, _ -> updateAdapterState() }
    private val stateTransaction = StateTransaction({ state }, { updateAdapterState() })

    // start with an empty adapter here
    private val commentTreeHelper = PostFragmentCommentTreeHelper()

    private val activeStateSubject = BehaviorSubject.create<Boolean>(false)
    private var scrollHandler: RecyclerView.OnScrollListener = NoopScrollHandler()

    private var fullscreenAnimator: ObjectAnimator? = null
    private var rewindOnNextLoad: Boolean = false

    private val apiComments = BehaviorSubject.create(listOf<Api.Comment>())
    private val apiTags = BehaviorSubject.create(listOf<Api.Tag>())


    private val settings = Settings.get()
    private val feedService: FeedService by instance()
    private val voteService: VoteService by instance()
    private val seenService: SeenService by instance()
    private val inMemoryCacheService: InMemoryCacheService by instance()
    private val userService: UserService by instance()
    private val downloadService: DownloadService by instance()
    private val configService: ConfigService by instance()

    private val swipeRefreshLayout: SwipeRefreshLayout by bindView(R.id.refresh)
    private val playerContainer: ViewGroup by bindView(R.id.player_container)
    private val recyclerView: StatefulRecyclerView? by bindOptionalView(R.id.post_content)
    private val recyclerViewInfo: RecyclerView? by bindOptionalView(R.id.post_content__info)
    private val recyclerViewComments: RecyclerView? by bindOptionalView(R.id.post_content__comments)
    private val voteAnimationIndicator: Pr0grammIconView by bindView(R.id.vote_indicator)
    private val repostHint: View by bindView(R.id.repost_hint)

    private var viewer: MediaView? = null

    // must only be accessed after injecting kodein
    private val feedItemVote: Observable<Vote> by lazy {
        voteService.getVote(feedItem).replay(1).refCount()
    }

    init {
        arguments = Bundle()
    }

    override fun onCreate(savedInstanceState: Bundle?): Unit = stateTransaction(dispatch = false) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        if (savedInstanceState != null) {
            val tags = Parceler.get(TagListParceler::class.java, savedInstanceState, "PostFragment.tags")
            if (tags != null) {
                this.apiTags.onNext(tags)
            }

            val comments = Parceler.get(CommentListParceler::class.java, savedInstanceState, "PostFragment.comments")
            if (comments != null) {
                this.apiComments.onNext(comments)
            }
        }

        // check if we are admin or not
        userService.loginStates.skip(1).observeOnMainThread().bindToLifecycle().subscribe {
            activity?.invalidateOptionsMenu()
        }

        // subscribe to it as long as the fragment lives.
        feedItemVote.ignoreError()
                .bindToLifecycleAsync()
                .subscribe()
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = stateTransaction {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity()

        (activity as ToolbarActivity?)?.scrollHideToolbarListener?.reset()

        val abHeight = AndroidUtility.getActionBarContentOffset(activity)

        // handle swipe to refresh
        swipeRefreshLayout.setColorSchemeResources(ThemeHelper.accentColor)
        swipeRefreshLayout.setProgressViewOffset(false, 0, (1.5 * abHeight).toInt())
        swipeRefreshLayout.setOnRefreshListener {
            if (!isVideoFullScreen) {
                rewindOnNextLoad = true
                loadItemDetails()
            }
        }

        // if the user requests "keep screen on", we apply the flag to the view of the fragment.
        // as long as the fragment is visible, the screen stays on.
        view.keepScreenOn = settings.keepScreenOn

        // default to no scrolling
        scrollHandler = NoopScrollHandler()

        recyclerView?.let { recyclerView ->
            // react to scrolling
            scrollHandler = ScrollHandler()

            recyclerView.addOnScrollListener(scrollHandler)

            recyclerView.itemAnimator = null
            recyclerView.layoutManager = recyclerView.LinearLayoutManager(getActivity())
            recyclerView.adapter = PostAdapter(commentTreeHelper, Actions())
        }

        recyclerViewInfo?.let { recyclerView ->
            recyclerView.itemAnimator = null
            recyclerView.layoutManager = LinearLayoutManager(getActivity())
            recyclerView.adapter = PostAdapter(commentTreeHelper, Actions())
        }

        recyclerViewComments?.let { recyclerView ->
            recyclerView.itemAnimator = DefaultItemAnimator().apply { supportsChangeAnimations = false }
            recyclerView.layoutManager = LinearLayoutManager(getActivity())
            recyclerView.adapter = PostAdapter(commentTreeHelper, Actions())
        }

        logger.time("Initialize media view") {
            initializeMediaView()
        }

        adapterComments.updates
                .observeOnMainThread()
                .bindToLifecycle()
                .subscribe { tryAutoScrollToCommentNow() }

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

        val tags = this.apiTags.value.orEmpty()
        val comments = this.apiComments.value.orEmpty()

        if (comments.isNotEmpty()) {
            // if we have saved comments we need to apply immediately to ensure
            // we can restore scroll position and stuff.
            updateComments(comments, updateSync = true)
        }

        if (tags.isNotEmpty()) {
            updateTags(tags)
        }

        // listen to comment changes
        commentTreeHelper.itemsObservable.subscribe { commentItems ->
            logger.info("Got new list of {} comments", commentItems.size)
            state = state.copy(comments = commentItems, commentsLoading = false)
        }

        // we do this after the first commentTreeHelper callback above
        if (comments.isEmpty() && tags.isEmpty()) {
            loadItemDetails(firstLoad = true)
        }

        apiTags.subscribe { hideProgressIfLoop(it) }

        feedItemVote.bindToLifecycleAsync().subscribe { vote ->
            state = state.copy(itemVote = vote)
        }

        // show the repost badge if this is a repost
        repostHint.visible = inMemoryCacheService.isRepost(feedItem)
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

    private fun updateAdapterState() {
        checkMainThread()

        if (stateTransaction.isActive) {
            return
        }

        val state = this.state

        debug {
            logger.debug("Applying post fragment state: h={}, tags={}, tagVotes={}, comments={} ({}), l={}, viewer={}, mcc={}",
                    state.viewerBaseHeight,
                    state.tags.size, state.tagVotes.size(),
                    state.comments.size, state.comments.hashCode(),
                    state.commentsLoading,
                    viewer != null, state.mediaControlsContainer != null)
        }

        val items = mutableListOf<PostAdapter.Item>()

        viewer?.let { viewer ->
            if (state.viewerBaseHeight > 0) {
                items += PostAdapter.Item.PlaceholderItem(state.viewerBaseHeight,
                        viewer, state.mediaControlsContainer)
            }
        }

        val isOurPost = userService.name.equals(state.item.user, ignoreCase = true)
        items += PostAdapter.Item.InfoItem(state.item, state.itemVote, isOurPost)
        items += PostAdapter.Item.TagsItem(state.tags, state.tagVotes)
        items += PostAdapter.Item.CommentInputItem(text = "")

        if (state.commentsVisible) {
            if (state.commentsLoadError) {
                items += PostAdapter.Item.LoadErrorItem
            } else {
                items += state.comments.map { PostAdapter.Item.CommentItem(it) }

                if (state.commentsLoading && state.comments.isEmpty()) {
                    items += PostAdapter.Item.CommentsLoadingItem
                }
            }
        }

        submitItemsToAdapter(items)
    }

    private fun submitItemsToAdapter(items: MutableList<PostAdapter.Item>) {
        recyclerView?.postAdapter?.submitList(items)

        recyclerViewInfo?.postAdapter?.let { adapter ->
            adapter.submitList(items.filter { item ->
                item is PostAdapter.Item.InfoItem || item is PostAdapter.Item.TagsItem
            })
        }

        recyclerViewComments?.postAdapter?.let { adapter ->
            adapter.submitList(items.filter { item ->
                item is PostAdapter.Item.CommentItem
                        || item is PostAdapter.Item.CommentInputItem
                        || item === PostAdapter.Item.CommentsLoadingItem
                        || item === PostAdapter.Item.LoadErrorItem
            })
        }
    }

    override fun onStart() {
        activeState.bindToLifecycle().subscribe { active ->
            logger.info("Switching viewer state to {}", active)
            if (active) {
                playMediaOnViewer()
            } else {
                stopMediaOnViewer()
            }

            if (!active) {
                exitFullscreen()
            }
        }

        super.onStart()
    }

    override fun onDestroyView() {
        recyclerView?.removeOnScrollListener(scrollHandler)

        activity?.let {
            // restore orientation if the user closes this view
            Screen.unlockOrientation(it)
        }

        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val tags = apiTags.value.orEmpty()
        if (tags.isNotEmpty()) {
            outState.putParcelable("PostFragment.tags", TagListParceler(tags))
        }

        val comments = apiComments.value.orEmpty()
        if (comments.isNotEmpty()) {
            outState.putParcelable("PostFragment.comments", CommentListParceler(comments))
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

        menu.findItem(R.id.action_refresh)
                ?.isVisible = settings.showRefreshButton && !isVideoFullScreen

        menu.findItem(R.id.action_zoom)
                ?.isVisible = !isVideoFullScreen && (!isTabletMode || isImage)

        menu.findItem(R.id.action_share_image)
                ?.isVisible = true

        menu.findItem(R.id.action_search_image)
                ?.isVisible = isImage && settings.showGoogleImageButton

        menu.findItem(R.id.action_delete_item)
                ?.isVisible = adminMode

        menu.findItem(R.id.action_tags_details)
                ?.isVisible = adminMode

        menu.findItem(R.id.action_report)
                ?.isVisible = config.reportItemsActive && userService.isAuthorized
    }

    @OnOptionsItemSelected(R.id.action_zoom)
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

            repostHint.visible = false

            // hide content below
            swipeRefreshLayout.visible = false

            if (activity is ToolbarActivity) {
                // hide the toolbar if required necessary
                activity.scrollHideToolbarListener.hide()
            }

            viewer.setClipBoundsCompat(null)
            viewer.visible = true

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

        swipeRefreshLayout.visible = true

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
        recyclerView?.postAdapter?.let { adapter ->
            val idx = adapter.items.indexOfFirst { it is PostAdapter.Item.PlaceholderItem }
            if (idx >= 0) {
                adapter.notifyItemChanged(idx)
            }
        }
    }

    internal val isVideoFullScreen: Boolean get() {
        return fullscreenAnimator != null
    }

    @OnOptionsItemSelected(MainActivity.ID_FAKE_HOME)
    fun onHomePressed(): Boolean {
        if (isVideoFullScreen) {
            exitFullscreen()
            return true
        }

        return false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val activity = activity ?: return true

        when (item.itemId) {
            R.id.action_search_image ->
                ShareHelper.searchImage(activity, feedItem)

            R.id.action_share_post ->
                ShareHelper.sharePost(activity, feedItem)

            R.id.action_share_direct_link ->
                ShareHelper.shareDirectLink(activity, feedItem)

            R.id.action_share_image ->
                ShareHelper.shareImage(activity, feedItem)

            R.id.action_copy_link ->
                ShareHelper.copyLink(context, feedItem)

            else -> return OptionMenuHelper.dispatch(this, item) || super.onOptionsItemSelected(item)
        }

        return true
    }

    @OnOptionsItemSelected(R.id.action_refresh)
    fun refreshWithIndicator() {
        if (swipeRefreshLayout.isRefreshing || isDetached)
            return

        rewindOnNextLoad = true
        swipeRefreshLayout.isRefreshing = true
        swipeRefreshLayout.postDelayed({ this.loadItemDetails() }, 500)
    }

    @OnOptionsItemSelected(R.id.action_download)
    fun downloadPostMedia() {
        (activity as PermissionHelperActivity)
                .requirePermission(WRITE_EXTERNAL_STORAGE)
                .compose(bindUntilEventAsync(FragmentEvent.DESTROY))
                .subscribeWithErrorHandling { downloadPostWithPermissionGranted() }
    }

    private fun downloadPostWithPermissionGranted() {
        val preview = previewInfo.preview?.let { it as? BitmapDrawable }?.bitmap
                ?: previewInfo.fancy?.valueOrNull

        downloadService
                .downloadWithNotification(feedItem, preview)
                .decoupleSubscribe()
                .bindToLifecycleAsync()
                .subscribeBy(onError = { err ->
                    if (err is DownloadService.CouldNotCreateDownloadDirectoryException) {
                        showErrorString(fragmentManager, getString(R.string.error_could_not_create_download_directory))
                    }
                })
    }

    override fun onResume() {
        super.onResume()

        apiComments
                .switchMap { comments ->
                    voteService.getCommentVotes(comments).subscribeOnBackground().onErrorResumeEmpty()
                }
                .observeOnMainThread()
                .bindToLifecycle()
                .subscribe { votes -> commentTreeHelper.updateVotes(votes) }

        apiTags
                .switchMap { votes ->
                    voteService.getTagVotes(votes).subscribeOnBackground().onErrorResumeEmpty()
                }
                .observeOnMainThread()
                .bindToLifecycle()
                .subscribe { votes -> state = state.copy(tagVotes = votes) }


        // observeOnMainThread uses post to scroll in the next frame.
        // this prevents the viewer from getting bad clipping.
        recyclerView?.postAdapter?.let { adapter ->
            adapter.updates.skip(1).observeOnMainThread().bindToLifecycle().subscribe {
                simulateScroll()
            }
        }

        // track that the user visited this post.
        if (configService.config().trackItemView) {
            Track.screen(activity, "Item")
        }
    }

    /**
     * Loads the information about the post. This includes the
     * tags and the comments.
     */
    private fun loadItemDetails(firstLoad: Boolean = false) {
        // postDelayed could execute this if it is not added anymore
        if (!isAdded || isDetached) {
            return
        }

        // update state to show "loading" items
        state = state.copy(
                commentsLoading = firstLoad || state.commentsLoadError || apiComments.value.isEmpty(),
                commentsLoadError = false)

        feedService.post(feedItem.id)
                .compose(bindUntilEventAsync(FragmentEvent.DESTROY_VIEW))
                .doOnError { err ->
                    if (Throwables.getRootCause(err) !is IOException) {
                        AndroidUtility.logToCrashlytics(err)
                    }

                    stateTransaction {
                        updateComments(emptyList(), updateSync = true)
                        state = state.copy(commentsLoadError = true, commentsLoading = false)
                    }
                }

                .doAfterTerminate { swipeRefreshLayout.isRefreshing = false }
                .ignoreError()
                .subscribe { onPostReceived(it) }
    }

    @OnOptionsItemSelected(R.id.action_delete_item)
    fun showDeleteItemDialog() {
        val dialog = ItemUserAdminDialog.forItem(feedItem)
        dialog.show(fragmentManager, null)
    }

    @OnOptionsItemSelected(R.id.action_tags_details)
    fun showTagsDetailsDialog() {
        val dialog = TagsDetailsDialog.newInstance(feedItem.id)
        dialog.show(fragmentManager, null)
    }

    @OnOptionsItemSelected(R.id.action_report)
    fun showReportDialog() {
        val dialog = ReportDialog.forItem(feedItem)
        dialog.show(fragmentManager, null)
    }

    private fun showPostVoteAnimation(vote: Vote?) {
        if (vote === null || vote === Vote.NEUTRAL)
            return

        // quickly center the vote button
        simulateScroll()

        val text = if (vote === Vote.UP) "+" else (if (vote === Vote.DOWN) "-" else "*")
        voteAnimationIndicator.text = text

        voteAnimationIndicator.visibility = View.VISIBLE
        voteAnimationIndicator.alpha = 0f
        voteAnimationIndicator.scaleX = 0.7f
        voteAnimationIndicator.scaleY = 0.7f

        ObjectAnimator.ofPropertyValuesHolder(voteAnimationIndicator,
                ofFloat(View.ALPHA, 0f, 0.6f, 0.7f, 0.6f, 0f),
                ofFloat(View.SCALE_X, 0.7f, 1.3f),
                ofFloat(View.SCALE_Y, 0.7f, 1.3f)).apply {

            start()

            addListener(hideViewEndAction(voteAnimationIndicator))
        }
    }

    private val isTabletMode: Boolean get() = recyclerViewInfo != null

    private fun initializeMediaView() {
        val activity = requireActivity()
        val uri = buildMediaUri()

        val viewerConfig = Config(activity, uri, audio = feedItem.audio, previewInfo = previewInfo)
        val viewer = logger.time("MediaView.newInstance") {
            MediaViews.newInstance(viewerConfig).also { this.viewer = it }
        }

        viewer.viewed().observeOn(BackgroundScheduler.instance()).subscribe {
            //  mark this item seen. We do that in a background thread
            seenService.markAsSeen(feedItem.id)
        }

        // inform viewer about fragment lifecycle events!
        MediaViews.adaptFragmentLifecycle(lifecycle(), viewer)

        registerTapListener(viewer)

        // add views in the correct order (normally first child)
        val idx = playerContainer.indexOfChild(voteAnimationIndicator)
        playerContainer.addView(viewer, idx)

        // Add a container for the children
        val mediaControlsContainer = FrameLayout(context)
        mediaControlsContainer.layoutParams = FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)

        // add space to the top of the viewer or to the screen to compensate
        // for the action bar.
        val viewerPaddingTop = AndroidUtility.getActionBarContentOffset(activity)
        if (isTabletMode) {
            view?.setPadding(0, viewerPaddingTop, 0, 0)

            playerContainer.addView(mediaControlsContainer)

            viewer.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER)

        } else {
            viewer.setPadding(0, viewerPaddingTop, 0, 0)

            if (feedItem.width > 0 && feedItem.height > 0) {
                val screenSize = Point().also { activity.windowManager.defaultDisplay.getSize(it) }
                val expectedMediaHeight = screenSize.x * feedItem.height / feedItem.width
                val expectedViewerHeight = expectedMediaHeight + viewerPaddingTop
                state = state.copy(viewerBaseHeight = expectedViewerHeight)

                logger.debug("Initialized viewer height to {}", expectedViewerHeight)
            }

            viewer.layoutChanges().map { Unit }.subscribe {
                val newHeight = viewer.measuredHeight
                if (newHeight != state.viewerBaseHeight) {
                    logger.debug("Change in viewer height detected, setting height to {} to {}",
                            state.viewerBaseHeight, newHeight)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        state = state.copy(viewerBaseHeight = newHeight)
                    } else {
                        // it looks like a requestLayout is not honored on pre kitkat devices
                        // if already in a layout pass.
                        viewer.post { state = state.copy(viewerBaseHeight = newHeight) }
                    }

                    if (isVideoFullScreen) {
                        realignFullScreen()
                    }
                }
            }

            state = state.copy(mediaControlsContainer = mediaControlsContainer)
        }


        // add the controls as child of the controls-container.
        viewer.controllerView()
                .compose(bindUntilEvent(FragmentEvent.DESTROY_VIEW))
                .doOnNext { view -> logger.info("Adding view {} to placeholder", view) }
                .subscribe { mediaControlsContainer.addView(it) }

        // show sfw/nsfw as a little flag, if the user is admin
        if (userService.userIsAdmin && settings.showContentTypeFlag) {
            // show the little admin triangle
            val size = AndroidUtility.dp(context, 16)
            ViewCompat.setBackground(mediaControlsContainer,
                    TriangleDrawable(feedItem.contentType, size))

            mediaControlsContainer.minimumHeight = size
        }
    }

    private fun buildMediaUri(): MediaUri {
        // initialize a new viewer fragment
        val uri = MediaUri.of(context, feedItem)

        if (!uri.isLocal && AndroidUtility.isOnMobile(context)) {
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

        return@lazy PreviewInfo.of(context, feedItem)
    }

    private fun simulateScroll() {
        val recyclerView = this.recyclerView ?: return

        val handler = scrollHandler
        if (handler is ScrollHandler) {
            handler.onScrolled(recyclerView, 0, 0)
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
        viewer.tapListener = object : MediaView.TapListener {
            val isImage = isStaticImage(feedItem)

            override fun onSingleTap(event: MotionEvent): Boolean {
                if (isImage && settings.singleTapForFullscreen) {
                    enterFullscreen()
                }

                return true
            }

            override fun onDoubleTap(): Boolean {
                if (settings.doubleTapToUpvote) {
                    doVoteOnDoubleTap()
                }

                return true
            }
        }
    }

    private fun doVoteOnDoubleTap() {
        feedItemVote.first().bindToLifecycleAsync().subscribe { currentVote ->
            doVoteFeedItem(currentVote.nextUpVote)
        }
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

        apiTags.onNext(tags)

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

        this.apiComments.onNext(comments.toList())

        // show comments now
        logger.info("Sending {} comments to tree helper", comments.size)
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
    }

    override fun onAddNewTags(tags: List<String>) {
        val activity = activity ?: return

        val previousTags = this.apiTags.value.orEmpty()

        // allow op to tag a more restrictive content type.
        val op = feedItem.user.equals(userService.name, true) || userService.userIsAdmin
        val newTags = tags.filter { tag ->
            isValidTag(tag) || (op && isMoreRestrictiveContentTypeTag(previousTags, tag))
        }

        if (newTags.isNotEmpty()) {
            logger.info("Adding new tags {} to post", newTags)

            voteService.tag(feedItem.id, newTags)
                    .bindToLifecycleAsync()
                    .lift(BusyDialog.busyDialog(activity))
                    .subscribeWithErrorHandling { updateTags(it) }
        }
    }

    private fun onNewComments(response: Api.NewComment) {
        autoScrollToComment(response.commentId, delayed = true)

        updateComments(response.comments) { state ->
            state.copy(selectedCommentId = response.commentId, baseVotes = state.baseVotes.let { votes ->
                val copy = TLongObjectHashMap(votes)
                copy.put(response.commentId, Vote.UP)
                copy
            })
        }

        view?.let { fragmentView ->
            Snackbar.make(fragmentView, R.string.comment_written_successful, Snackbar.LENGTH_LONG)
                    .configureNewStyle()
                    .setAction(R.string.okay) {}
                    .show()
        }
    }

    fun autoScrollToComment(commentId: Long, delayed: Boolean = false) {
        arguments?.putLong(ARG_AUTOSCROLL_COMMENT_ID, commentId)

        if (!delayed) {
            tryAutoScrollToCommentNow()
        }
    }

    private fun tryAutoScrollToCommentNow() {
        val commentId = arguments?.getLong(ARG_AUTOSCROLL_COMMENT_ID) ?: return

        // get the current recycler view and adapter.
        val recyclerView = recyclerView ?: recyclerViewComments ?: return
        val adapter = recyclerView.postAdapter ?: return

        val idx = adapter.items.indexOfFirst { item ->
            item is PostAdapter.Item.CommentItem && item.commentTreeItem.commentId == commentId
        }

        if (idx >= 0) {
            recyclerView.scrollToPosition(idx)

            commentTreeHelper.selectComment(commentId)
            arguments?.remove(ARG_AUTOSCROLL_COMMENT_ID)
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
            val doFancyScroll = viewerHeight < recyclerHeight

            val toolbar = (activity as ToolbarActivity).scrollHideToolbarListener
            if (!doFancyScroll || dy < 0 || scrollY > toolbar.toolbarHeight) {
                toolbar.onScrolled(dy)
            }

            val scroll = if (doFancyScroll) 0.7f * scrollY else scrollY.toFloat()

            if (doFancyScroll) {
                val clipTop = (scroll + 0.5f).toInt()
                val clipBottom = viewer.height - (scrollY - scroll + 0.5f).toInt()

                if (clipTop < clipBottom) {
                    viewer.setClipBoundsCompat(Rect(0, clipTop, viewer.right, clipBottom))
                } else {
                    viewerVisible = false
                }
            } else {
                // reset bounds. we might have set some previously and want
                // to clear those bounds now.
                viewer.setClipBoundsCompat(null)
            }

            offsetMediaView(viewerVisible, scroll)

            // position the vote indicator
            val remaining = (viewerHeight - scrollY).toFloat()
            val tbVisibleHeight = toolbar.visibleHeight
            val voteIndicatorY = Math.min(
                    (remaining - tbVisibleHeight) / 2,
                    ((recyclerHeight - tbVisibleHeight) / 2).toFloat()) + tbVisibleHeight

            voteAnimationIndicator.translationY = voteIndicatorY
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (!isVideoFullScreen && newState == RecyclerView.SCROLL_STATE_IDLE) {
                val y = ScrollHideToolbarListener.estimateRecyclerViewScrollY(recyclerView) ?: Integer.MAX_VALUE
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
                repostHint.visible = true
                repostHint.translationY = viewer.paddingTop.toFloat() - repostHint.pivotY - offset
            }
        } else {
            viewer.visible = false
            repostHint.visible = false
        }
    }

    /**
     * Returns true, if the given tag looks like some "loop" tag.
     */
    private fun Api.Tag.isLoopTag(): Boolean {
        val lower = tag.toLowerCase()
        return "loop" in lower && !("verschenkt" in lower || "verkackt" in lower)
    }

    /**
     * Returns true, if the given url links to a static image.
     * This does only a check on the filename and not on the data.

     * @param image The url of the image to check
     */
    private fun isStaticImage(image: FeedItem): Boolean {
        return image.image.toLowerCase().matches((".*\\.(jpg|jpeg|png)").toRegex())
    }

    private fun doVoteFeedItem(vote: Vote): Boolean {
        val action = Runnable {
            showPostVoteAnimation(vote)

            voteService.vote(feedItem, vote)
                    .decoupleSubscribe()
                    .compose(bindToLifecycleAsync<Any>())
                    .subscribeWithErrorHandling()
        }

        return doIfAuthorizedHelper.run(action, action)
    }

    private val adapterComments: PostAdapter
        get() = recyclerView?.postAdapter
                ?: recyclerViewComments?.postAdapter
                ?: throw IllegalStateException("no comment adapter set")

    private val adapterInfo: PostAdapter
        get() = recyclerView?.postAdapter
                ?: recyclerViewInfo?.postAdapter
                ?: throw IllegalStateException("no info adapter set")

    inner class PostFragmentCommentTreeHelper : CommentTreeHelper() {
        override fun onCommentVoteClicked(comment: Api.Comment, vote: Vote): Boolean {
            return doIfAuthorizedHelper.run(Runnable {
                voteService.vote(comment, vote)
                        .decoupleSubscribe()
                        .compose(bindUntilEventAsync(FragmentEvent.DESTROY_VIEW))
                        .subscribeWithErrorHandling()
            })
        }

        override fun onReplyClicked(comment: Api.Comment) {
            val retry = Runnable { onReplyClicked(comment) }

            doIfAuthorizedHelper.run(Runnable {
                startActivityForResult(
                        WriteMessageActivity.answerToComment(context, feedItem, comment),
                        RequestCodes.WRITE_COMMENT)

            }, retry)
        }

        override fun onCommentAuthorClicked(comment: Api.Comment) {
            (parentFragment as PostPagerFragment).onUsernameClicked(comment.name)
        }

        override fun onCopyCommentLink(comment: Api.Comment) {
            ShareHelper.copyLink(context, feedItem, comment)
        }

        override fun onDeleteCommentClicked(comment: Api.Comment): Boolean {
            val dialog = DeleteCommentDialog.newInstance(comment.id)
            dialog.show(fragmentManager, null)
            return true
        }

        override fun onReportCommentClicked(comment: Api.Comment) {
            val dialog = ReportDialog.forComment(feedItem, comment.id)
            dialog.show(fragmentManager, null)
        }
    }

    private inner class Actions : PostActions {
        override fun voteTagClicked(tag: Api.Tag, vote: Vote): Boolean {
            // and a vote listener vor voting tags.
            val action = Runnable {
                voteService.vote(tag, vote)
                        .decoupleSubscribe()
                        .compose(bindToLifecycleAsync<Any>())
                        .subscribeWithErrorHandling()
            }

            return doIfAuthorizedHelper.run(action, action)
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
                val dialog = NewTagDialogFragment()
                dialog.show(childFragmentManager, null)
            }
        }

        override fun writeCommentClicked(text: String): Boolean {
            AndroidUtility.hideSoftKeyboard(view)

            val action = Runnable {
                voteService.postComment(feedItem, 0, text)
                        .bindToLifecycleAsync()
                        .lift(BusyDialog.busyDialog(context))
                        .subscribeWithErrorHandling { onNewComments(it) }
            }

            return doIfAuthorizedHelper.run(action, action)
        }
    }

    private data class FragmentState(
            val item: FeedItem,
            val itemVote: Vote = Vote.NEUTRAL,
            val tags: List<Api.Tag> = emptyList(),
            val tagVotes: TLongObjectMap<Vote> = TLongObjectHashMap(),
            val viewerBaseHeight: Int = 0,
            val comments: List<CommentTree.Item> = emptyList(),
            val commentsVisible: Boolean = true,
            val commentsLoading: Boolean = false,
            val commentsLoadError: Boolean = false,
            val mediaControlsContainer: View? = null)

    companion object {
        const val ARG_FEED_ITEM = "PostFragment.post"
        const val ARG_COMMENT_DRAFT = "PostFragment.comment-draft"
        const val ARG_AUTOSCROLL_COMMENT_ID = "PostFragment.first-comment"

        /**
         * Creates a new instance of a [PostFragment] displaying the
         * given [FeedItem].
         */
        fun newInstance(item: FeedItem): PostFragment {
            return PostFragment().arguments {
                putParcelable(ARG_FEED_ITEM, item)
            }
        }

        private val RecyclerView.postAdapter: PostAdapter? get() = adapter as? PostAdapter
    }
}
