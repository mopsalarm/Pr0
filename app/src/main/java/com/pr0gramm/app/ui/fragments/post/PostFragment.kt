package com.pr0gramm.app.ui.fragments.post

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
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.whenResumed
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.leinardi.android.speeddial.SpeedDialView
import com.pr0gramm.app.*
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.parcel.getParcelableOrThrow
import com.pr0gramm.app.services.*
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.ui.*
import com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity
import com.pr0gramm.app.ui.back.BackAwareFragment
import com.pr0gramm.app.ui.base.*
import com.pr0gramm.app.ui.dialogs.CollectionsSelectionDialog
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.showErrorString
import com.pr0gramm.app.ui.dialogs.NewTagDialogFragment
import com.pr0gramm.app.ui.fragments.*
import com.pr0gramm.app.ui.fragments.feed.update
import com.pr0gramm.app.ui.views.PostActions
import com.pr0gramm.app.ui.views.viewer.*
import com.pr0gramm.app.ui.views.viewer.MediaView.Config
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.instance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.internal.toHexString
import java.util.*
import kotlin.math.min

/**
 * This fragment shows the content of one post.
 */
class PostFragment : BaseFragment("PostFragment"), NewTagDialogFragment.OnAddNewTagsListener, TitleFragment, BackAwareFragment {
    /**
     * The item that is displayed
     */
    val feedItem: FeedItem by lazy {
        requireArguments().getParcelableOrThrow(ARG_FEED_ITEM)
    }

    private val doIfAuthorizedHelper = LoginActivity.helper(this)

    private val model by viewModels {
        val requiresCacheBust = true == commentRef?.notificationTime?.let { notificationTime ->
            val threshold = Instant.now().minus(Duration.seconds(60))
            notificationTime.isAfter(threshold)
        }

        PostViewModel(
                feedItem, requiresCacheBust,
                userService = instance(),
                feedService = instance(),
                voteService = instance(),
                followService = instance(),
                inMemoryCacheService = instance(),
        )
    }


    // start with an empty adapter here
    private val commentTreeHelper = CommentListener()

    private val activeState = MutableStateFlow(false)

    private var fullscreenAnimator: ObjectAnimator? = null

    private var commentRef: CommentRef? by optionalFragmentArgument(name = ARG_COMMENT_REF)

    private val settings = Settings.get()
    private val voteService: VoteService by instance()
    private val favedCommentService: FavedCommentService by instance()
    private val seenService: SeenService by instance()
    private val followService: FollowService by instance()
    private val inMemoryCacheService: InMemoryCacheService by instance()
    private val userService: UserService by instance()
    private val downloadService: DownloadService by instance()
    private val configService: ConfigService by instance()
    private val shareService: ShareService by instance()
    private val interstitialAdler by lazy { InterstitialAdler(requireContext()) }

    private val swipeRefreshLayout: SwipeRefreshLayout by bindView(R.id.refresh)
    private val playerContainer: ViewGroup by bindView(R.id.player_container)
    private val recyclerView: StatefulRecyclerView by bindView(R.id.post_content)
    private val voteAnimationIndicator: ImageView by bindView(R.id.vote_indicator)
    private val repostHint: View by bindView(R.id.repost_hint)
    private val speedDialView: SpeedDialView by bindView(R.id.speed_dial)

    // only set while we have a viewScope
    private var mediaViewState: StateFlow<MediaViewState>? = null

    private var viewer: MediaView? = null

    override var title: TitleFragment.Title = TitleFragment.Title("pr0gramm")

    override fun onCreate(savedInstanceState: Bundle?): Unit {
        super.onCreate(savedInstanceState)

        launchWhenStarted {
            userService.loginStates.drop(1).collect {
                activity?.invalidateOptionsMenu()
            }
        }

        debugOnly {
            MainScope.launch {
                lifecycle.asEventFlow().collect { event ->
                    this@PostFragment.trace { "${feedItem.id}: $event" }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_post, container, false) as ViewGroup
        addWarnOverlayIfNecessary(inflater, view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity()

        (activity as ToolbarActivity?)?.scrollHideToolbarListener?.reset()

        val abHeight = AndroidUtility.getActionBarContentOffset(activity)

        // handle swipe to refresh
        swipeRefreshLayout.setColorSchemeResources(ThemeHelper.accentColor)
        swipeRefreshLayout.setProgressViewOffset(false, 0, (1.5 * abHeight).toInt())
        swipeRefreshLayout.setOnRefreshListener {
            if (!isVideoFullScreen) {
                model.refreshAsync()

                // rewind video on refresh
                viewer?.rewind()
            }
        }

        // apply the flag to the view of the fragment.
        // as long as the fragment is visible, the screen stays on.
        view.keepScreenOn = true

        recyclerView.primaryScrollListener = ScrollHandler()

        recyclerView.itemAnimator = null
        recyclerView.layoutManager = recyclerView.LinearLayoutManager(getActivity())
        recyclerView.adapter = PostAdapter()

        if (activity is RecyclerViewPoolProvider) {
            activity.configureRecyclerView("Post", recyclerView)
        }

        val mediaViewState = initializeMediaView()

        this.mediaViewState = mediaViewState

        // show the repost badge if this is a repost
        repostHint.isVisible = inMemoryCacheService.isRepost(feedItem)

        speedDialView.inflate(R.menu.menu_post_dial)
        speedDialView.overlayLayout = view.find(R.id.overlay)

        speedDialView.setOnActionSelectedListener { actionItem ->
            when (actionItem.id) {
                R.id.action_write_comment -> {
                    val intent = WriteMessageActivity.newComment(requireActivity(), feedItem)
                    startActivityForResult(intent, RequestCodes.WRITE_COMMENT)
                }

                R.id.action_write_tag -> {
                    actions.writeNewTagClicked()
                }

                R.id.action_scroll_to_top -> {
                    recyclerView.smoothScrollToPosition(0)
                }

                R.id.action_collapse_all -> {
                    model.collapseComments()
                }
            }

            speedDialView.close()
            true
        }

        launchInViewScope {
            class State(val modelState: PostViewModel.State, val mediaViewState: MediaViewState)

            val combinedState = combine(model.state, mediaViewState) { modelState, mediaViewState ->
                State(modelState, mediaViewState)
            }

            combinedState.collect { state ->
                updateAdapterFromState(state.modelState, state.mediaViewState)

                if (!state.modelState.refreshing) {
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }

        launchInViewScope {
            postAdapter.state.collect {
                tryAutoScrollToCommentNow(smoothScroll = false)
            }
        }

        launchInViewScope {
            whenResumed {
                model.state.distinctUntilChangedBy { it.tags }.collect { state ->
                    hideProgressIfLoop(state.tags)
                    updateTitle(state.tags)
                }
            }
        }

        launchInViewScope {
            val activeAndResumed = combine(activeState, lifecycle.asStateFlow()) { active, state ->
                active && state.isAtLeast(Lifecycle.State.RESUMED)
            }

            activeAndResumed.distinctUntilChanged().collect { active ->
                trace { "${feedItem.id}.activeState($active): Switching viewer state" }

                if (active) {
                    playMediaOnViewer()
                } else {
                    stopMediaOnViewer()
                }

                if (!active) {
                    exitFullscreen()
                }

                uiTestOnly {
                    view.setTag(R.id.ui_test_activestate, active)
                }
            }
        }

        // this prevents the viewer from getting bad clipping.
        launchInViewScope {
            postAdapter.state.drop(1).collect {
                simulateScroll()
            }
        }
    }

    private fun stopMediaOnViewer() {
        viewer?.stopMedia()
    }

    private fun playMediaOnViewer() {
        viewer?.playMedia()
    }

    private fun updateTitle(tags: List<Api.Tag>) {
        val exclude = setOf(
                "sfw", "nsfw", "nsfl", "nsfp", "gif", "video", "sound",
                "text", "porn", "richtiges grau", "achtung laut", "repost", "loop")

        // take the best rated tag that is not excluded
        val title = tags.sortedByDescending { it.confidence }.firstOrNull {
            val tag = it.text.toLowerCase(Locale.GERMANY)
            tag !in exclude && "loop" !in tag
        } ?: return

        // use the tag as the title for this fragment.
        this.title = TitleFragment.Title(title.text)

        // and ping the activity to update the title
        val mainActivity = activity as? MainActivity
        mainActivity?.updateActionbarTitle()
    }

    private fun updateAdapterFromState(state: PostViewModel.State, mediaViewState: MediaViewState) {
        val viewerBaseHeight = mediaViewState.height
        val mediaControlsContainer: ViewGroup? = mediaViewState.controlsContainer

        logger.debug {
            "Applying post fragment state: h=${viewerBaseHeight}, refreshing=${state.refreshing}, " +
                    "itemVote=${state.itemVote}, " +
                    "tags=${state.tags.size}, tagVotes=${state.tagVotes.size}/${state.tagVotes.hashCode().toHexString()}, " +
                    "comments=${state.comments.size}/${state.comments.hashCode()}, " +
                    "loading=${state.commentsLoading}, commentsVisible=${state.commentsVisible}, " +
                    "followState=${state.followState}, " +
                    "mcc=${mediaControlsContainer?.identityHashCode()?.toHexString()}"
        }

        val items = mutableListOf<PostAdapter.Item>()

        viewer?.let { viewer ->
            if (viewerBaseHeight > 0) {
                items += PostAdapter.Item.PlaceholderItem(viewerBaseHeight,
                        viewer, mediaControlsContainer)
            }
        }

        val isOurPost = userService.name.equals(state.item.user, ignoreCase = true)
        items += PostAdapter.Item.InfoItem(state.item, state.itemVote, isOurPost, state.followState, actions)

        if (state.item.deleted) {
            items += PostAdapter.Item.PostIsDeletedItem

        } else {
            items += PostAdapter.Item.TagsItem(state.item.id, state.tags, state.tagVotes, actions)

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

        postAdapter.submitList(items)
    }

    override fun onDestroyView() {
        recyclerView.primaryScrollListener = null

        activity?.let {
            // restore orientation if the user closes this view
            Screen.unlockOrientation(it)
        }

        // reset state flow for previous view.
        mediaViewState = null

        super.onDestroyView()
    }

    private fun addWarnOverlayIfNecessary(inflater: LayoutInflater, view: ViewGroup) {
        // add a view over the main view, if the post is not visible now
        if (userService.isAuthorized && feedItem.contentType !in settings.contentType) {
            val overlay = inflater.inflate(R.layout.warn_post_can_not_be_viewed, view, false)
            view.addView(overlay)

            // link the hide button
            val button = overlay.findViewById<View>(R.id.hide_warning_button)
            button.setOnClickListener { overlay.removeFromParent() }

            // force video views not to automatically enable sound on startup
            VolumeController.resetMuteTime(requireContext())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        doIfAuthorizedHelper.onActivityResult(requestCode, resultCode)


        if (resultCode == Activity.RESULT_OK && data != null) {
            if (requestCode == RequestCodes.WRITE_COMMENT) {
                onNewComments(WriteMessageActivity.getNewCommentFromActivityResult(data))
            }

            if (requestCode == RequestCodes.SELECT_DOWNLOAD_PATH) {
                if (!Storage.persistTreeUri(requireContext(), data)) {
                    showDialog(this) {
                        content(R.string.error_invalid_download_directory)
                        positive(R.string.okay)
                    }
                }
            }
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

    private fun enterFullscreen() {
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
            swipeRefreshLayout.isVisible = false

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

            // move media controls directly to viewer
            currentMediaViewState().controlsContainer?.let { mcc ->
                mcc.removeFromParent()
                viewer.addView(mcc)
            }
        }
    }

    private fun currentMediaViewState(): MediaViewState {
        return mediaViewState?.value ?: throw IllegalStateException("must in view lifecycle.")
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

        swipeRefreshLayout.isVisible = true

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

        Screen.unlockOrientation(activity)

        // gets attached to different parent somewhere else
        currentMediaViewState().controlsContainer?.removeFromParent()

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
        launchWhenStarted(busyIndicator = true) {
            shareService.shareImage(requireActivity(), feedItem)
        }
    }

    private fun refreshWithIndicator() {
        if (!swipeRefreshLayout.isRefreshing) {
            swipeRefreshLayout.isRefreshing = true
            swipeRefreshLayout.postDelayed({ model.refreshAsync() }, 500)
        }
    }

    private fun downloadPostMedia() {
        if (!Storage.hasTreeUri(requireContext())) {
            val intent = Storage.openTreeIntent(requireContext())

            ignoreAllExceptions {
                val noActivityAvailable = requireContext().packageManager.queryIntentActivities(intent, 0).isEmpty()
                if (noActivityAvailable) {
                    showNoFileManagerAvailable()
                    return
                }
            }

            showDialog(this) {
                content(R.string.hint_select_download_directory)
                positive {
                    startActivityForResult(intent, RequestCodes.SELECT_DOWNLOAD_PATH)
                }
            }

            return
        }

        downloadPostWithPermissionGranted()
    }

    private fun showNoFileManagerAvailable() {
        showDialog(this) {
            content(R.string.hint_no_file_manager_available)
            positive(R.string.okay)
        }
    }

    private fun downloadPostWithPermissionGranted() {
        val bitmapDrawable = previewInfo.preview as? BitmapDrawable
        val preview = bitmapDrawable?.bitmap ?: previewInfo.fancy?.valueOrNull

        launchWhenStarted {
            try {
                downloadService.downloadWithNotification(feedItem, preview)
            } catch (_: DownloadService.CouldNotCreateDownloadDirectoryException) {
                showErrorString(parentFragmentManager, getString(R.string.error_could_not_create_download_directory))
            }
        }
    }

    override fun onResume() {
        super.onResume()

        setHasOptionsMenu(true)
        setActive(true)
    }

    override fun onPause() {

        setActive(false)
        super.onPause()
    }

    private fun showDeleteItemDialog() {
        val dialog = ItemUserAdminDialog.forItem(feedItem)
        dialog.maybeShow(parentFragmentManager)
    }

    private fun showTagsDetailsDialog() {
        val dialog = TagsDetailsDialog.newInstance(feedItem.id)
        dialog.maybeShow(parentFragmentManager)
    }

    private fun showReportDialog() {
        val dialog = ReportDialog.forItem(feedItem)
        dialog.maybeShow(parentFragmentManager)
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

    private fun initializeMediaView(): StateFlow<MediaViewState> {
        val activity = requireActivity()
        val uri = buildMediaUri()

        val viewerConfig = Config(activity, uri, audio = feedItem.audio, previewInfo = previewInfo)
        val viewer = logger.time("MediaView.newInstance(${uri.baseUri})") {
            MediaViews.newInstance(viewerConfig)
        }

        viewer.tag = ViewerTag

        // remember for later
        this.viewer = viewer

        viewer.wasViewed = {
            doInBackground { seenService.markAsSeen(feedItem.id) }
        }

        registerTapListener(viewer)

        // add views in the correct order (normally first child)
        val idx = playerContainer.indexOfChild(voteAnimationIndicator)
        playerContainer.addView(viewer, idx)

        // Add a container for the children
        val mediaControlsContainer = FrameLayout(requireContext())
        mediaControlsContainer.layoutParams = FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)

        // initialize the view state
        val mediaViewState = MutableStateFlow(MediaViewState(
                controlsContainer = mediaControlsContainer,
        ))

        // add space to the top of the viewer or to the screen to compensate
        // for the action bar.
        val viewerPaddingTop = AndroidUtility.getActionBarContentOffset(activity)
        viewer.updatePadding(top = viewerPaddingTop)

        if (feedItem.width > 0 && feedItem.height > 0) {
            val screenSize = Point().also { activity.windowManager.defaultDisplay.getSize(it) }
            val expectedMediaHeight = screenSize.x * feedItem.height / feedItem.width
            val expectedViewerHeight = expectedMediaHeight + viewerPaddingTop

            logger.debug { "Initialize viewer height to $expectedViewerHeight" }

            mediaViewState.update { previousState ->
                previousState.copy(height = expectedViewerHeight)
            }
        }

        viewer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val newHeight = viewer.measuredHeight
            val oldHeight = mediaViewState.value.height

            if (newHeight != oldHeight) {
                logger.debug { "Change in viewer height detected, setting height to $oldHeight to $newHeight" }

                mediaViewState.update { previousState ->
                    previousState.copy(height = newHeight)
                }

                if (isVideoFullScreen) {
                    realignFullScreen()
                }
            }
        }

        launchInViewScope {
            viewer.controllerViews().collect { view ->
                logger.debug { "Adding view to media container: $view" }
                mediaControlsContainer.addView(view)
            }
        }

        if (settings.showContentTypeFlag) {
            // show the little admin triangle
            mediaControlsContainer.background = TriangleDrawable(feedItem.contentType, activity.dp(16))
            mediaControlsContainer.minimumHeight = activity.dp(16)
        }

        return mediaViewState
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
        recyclerView.primaryScrollListener?.onScrolled(recyclerView, 0, 0)
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
            override fun onSingleTap(event: MotionEvent): Boolean {
                executeTapAction(settings.singleTapAction)
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                executeTapAction(settings.doubleTapAction)
                return true
            }
        }
    }

    private fun executeTapAction(action: Settings.TapAction) {
        when (action) {
            Settings.TapAction.NONE -> Unit

            Settings.TapAction.FULLSCREEN ->
                if (isVideoFullScreen) {
                    exitFullscreen()
                } else {
                    enterFullscreen()
                }

            Settings.TapAction.UPVOTE ->
                doVoteOnDoubleTap(Vote.UP)

            Settings.TapAction.DOWNVOTE ->
                doVoteOnDoubleTap(Vote.DOWN)

            Settings.TapAction.COLLECT ->
                doCollectOnDoubleTap()
        }
    }

    private fun doVoteOnDoubleTap(targetVote: Vote) {
        launchWhenStarted {
            if (voteService.getItemVote(feedItem.id).first() != targetVote) {
                doVoteFeedItem(targetVote)
            } else {
                doVoteFeedItem(Vote.NEUTRAL)
            }
        }
    }

    private fun doCollectOnDoubleTap() {
        collectClicked()
    }

    private fun collectClicked() {
        doIfAuthorizedHelper.runAuthNoRetry {
            CollectionsSelectionDialog.addToCollection(this@PostFragment, feedItem.id)
        }
    }

    /**
     * If the current post is a loop, we'll check if it is a loop. If it is,
     * we will hide the little video progress bar.
     */
    private fun hideProgressIfLoop(tags: List<Api.Tag>) {
        val actualView = viewer?.actualMediaView

        if (actualView is AbstractProgressMediaView) {
            if (tags.any { tag -> isLoopTag(tag) }) {
                actualView.hideVideoProgress()
            }
        }
    }

    private fun isLoopTag(tag: Api.Tag): Boolean {
        val lower = tag.text.toLowerCase(Locale.GERMANY)
        return "loop" in lower && !("verschenkt" in lower || "verkackt" in lower)
    }

    /**
     * Called from the [PostPagerFragment] if this fragment
     * is currently the active/selected fragment - or if it is not the active fragment anymore.

     * @param active The new active status.
     */
    private fun setActive(active: Boolean) {
        activeState.value = active

        if (active) {
            Track.viewItem(feedItem.id)
        }
    }

    override fun onNewTags(tags: List<String>) {
        this.model.addTagsByUserAsync(tags)
    }

    private fun onNewComments(response: Api.NewComment) {
        val commentId = response.commentId
        if (commentId != null) {
            autoScrollToComment(commentId, delayed = true)
        }

        model.updateComments(response.comments, response.commentId)

        view?.let { fragmentView ->
            Snackbar.make(fragmentView, R.string.comment_written_successful, Snackbar.LENGTH_LONG)
                    .configureNewStyle()
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

        val idx = postAdapter.items.indexOfFirst { item ->
            item is PostAdapter.Item.CommentItem && item.commentTreeItem.commentId == commentId
        }

        if (idx >= 0) {
            val lm = recyclerView.layoutManager as LinearLayoutManager

            if (smoothScroll) {
                val scroller = CenterLinearSmoothScroller(recyclerView.context, idx)
                lm.startSmoothScroll(scroller)

            } else {
                // scroll the comment in the top quarter of the screen
                val offset = requireView().height * 1 / 4
                lm.scrollToPositionWithOffset(idx, offset)
            }

            model.selectComment(commentId)

            commentRef = null
        }
    }

    override fun onBackButton(): Boolean {
        if (isVideoFullScreen) {
            exitFullscreen()
            return true
        }

        return false
    }

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

            launchWhenStarted {
                withContext(NonCancellable + Dispatchers.Default) {
                    voteService.vote(feedItem, vote)
                }
            }
        }
    }

    private val postAdapter: PostAdapter
        get() = recyclerView.postAdapter ?: throw IllegalStateException("no post adapter set")

    inner class CommentListener : CommentView.Listener {
        override fun onCommentVoteClicked(comment: Api.Comment, vote: Vote): Boolean {
            return doIfAuthorizedHelper.runAuthNoRetry {
                launchWhenStarted {
                    voteService.vote(comment, vote)
                }
            }
        }

        override fun onCommentFavedClicked(comment: Api.Comment, faved: Boolean): Boolean {
            return doIfAuthorizedHelper.runAuthNoRetry {
                launchWhenStarted {
                    if (faved) {
                        favedCommentService.markAsFaved(comment.id)
                    } else {
                        favedCommentService.markAsNotFaved(comment.id)
                    }
                }
            }
        }

        override fun onReplyClicked(comment: Api.Comment) {
            val allComments = model.state.value.comments.map { it.comment }

            val parentComments = mutableListOf<WriteMessageActivity.ParentComment>()

            var current: Api.Comment? = comment
            while (current != null) {
                parentComments.add(WriteMessageActivity.ParentComment.ofComment(current))

                val parentId = current.parentId
                current = allComments.firstOrNull { it.id == parentId }
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
            dialog.maybeShow(parentFragmentManager)
            return true
        }

        override fun onBlockUserClicked(comment: Api.Comment): Boolean {
            val dialog = ItemUserAdminDialog.forUser(comment.name)
            dialog.maybeShow(parentFragmentManager)
            return true
        }

        override fun onReportCommentClicked(comment: Api.Comment) {
            val dialog = ReportDialog.forComment(feedItem, comment.id)
            dialog.maybeShow(parentFragmentManager)
        }

        override fun collapseComment(comment: Api.Comment) {
            model.collapseComment(comment.id)
        }

        override fun expandComment(comment: Api.Comment) {
            model.expandComment(comment.id)
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
                            .configureNewStyle()
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
                launchWhenStarted {
                    withContext(NonCancellable + Dispatchers.Default) {
                        voteService.vote(tag, vote)
                    }
                }
            }
        }

        override fun onTagClicked(tag: Api.Tag) {
            interstitialAdler.runWithAd {
                val parent = parentFragment as PostPagerFragment
                parent.onTagClicked(tag)
            }
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

        override suspend fun writeCommentClicked(text: String) {
            doIfAuthorizedHelper.runAuthNoRetrySuspend {
                onNewComments(voteService.postComment(feedItem.id, 0, text))
            }
        }

        override suspend fun updateFollowUser(follow: FollowState): Boolean {
            return doIfAuthorizedHelper.runAuthNoRetrySuspend {
                followService.update(follow, feedItem.userId, feedItem.user)
            }
        }

        override fun collectClicked() {
            this@PostFragment.collectClicked()
        }

        override fun showCollectionsClicked() {
            doIfAuthorizedHelper.runAuthNoRetry {
                val dialog = CollectionsSelectionDialog.newInstance(feedItem.id)
                dialog.show(childFragmentManager, null)
            }
        }
    }

    private data class MediaViewState(
            val height: Int = 0,
            val controlsContainer: ViewGroup? = null,
    )

    companion object {
        private const val ARG_FEED_ITEM = "PF.post"
        private const val ARG_COMMENT_REF = "PF.commentRef"

        /**
         * Creates a new instance of a [PostFragment] displaying the
         * given [FeedItem].
         */
        fun newInstance(item: FeedItem, commentRef: CommentRef? = null): PostFragment {
            return PostFragment().arguments {
                putParcelable(ARG_FEED_ITEM, item)
                putParcelable(ARG_COMMENT_REF, commentRef)
            }
        }

        private val RecyclerView.postAdapter: PostAdapter? get() = adapter as? PostAdapter

        val ViewerTag = Any()
    }
}
