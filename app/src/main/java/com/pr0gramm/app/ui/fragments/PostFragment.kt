package com.pr0gramm.app.ui.fragments

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder.ofFloat
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.view.ViewCompat
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.graphics.Palette
import android.support.v7.graphics.Target.DARK_MUTED
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.view.ViewGroup.LayoutParams
import android.widget.FrameLayout
import com.github.salomonbrys.kodein.instance
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.images.WebImage
import com.jakewharton.rxbinding.view.RxView
import com.pr0gramm.app.R
import com.pr0gramm.app.R.id.player_container
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
import com.pr0gramm.app.ui.views.CommentPostLine
import com.pr0gramm.app.ui.views.CommentsAdapter
import com.pr0gramm.app.ui.views.InfoLineView
import com.pr0gramm.app.ui.views.Pr0grammIconView
import com.pr0gramm.app.ui.views.viewer.AbstractProgressMediaView
import com.pr0gramm.app.ui.views.viewer.MediaUri
import com.pr0gramm.app.ui.views.viewer.MediaView
import com.pr0gramm.app.ui.views.viewer.MediaViews
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.AndroidUtility.dp
import com.trello.rxlifecycle.android.FragmentEvent
import rx.Completable
import rx.Observable
import rx.Observable.combineLatest
import rx.Observable.just
import rx.android.schedulers.AndroidSchedulers.mainThread
import rx.lang.kotlin.subscribeBy
import rx.subjects.BehaviorSubject

/**
 * This fragment shows the content of one post.
 */
class PostFragment : BaseFragment("PostFragment"), NewTagDialogFragment.OnAddNewTagsListener, BackAwareFragment, CommentsAdapter.CommentActionListener, InfoLineView.OnDetailClickedListener {
    /**
     * Returns the feed item that is displayed in this [PostFragment].
     */
    val feedItem: FeedItem by lazy { arguments.getParcelable<FeedItem>(ARG_FEED_ITEM) }

    private val adapter = MergeRecyclerAdapter()
    private val doIfAuthorizedHelper = LoginActivity.helper(this)
    private val activeStateSubject = BehaviorSubject.create<Boolean>(false)

    // start with an empty adapter here
    private var commentsAdapter: CommentsAdapter = CommentsAdapter(false, "")
    private var scrollHandler: RecyclerView.OnScrollListener? = null
    private var fullscreenAnimator: ObjectAnimator? = null

    private var tags: List<Api.Tag> = listOf()
    private var comments: List<Api.Comment> = listOf()
    private var rewindOnLoad: Boolean = false
    private var adminMode: Boolean = false

    private val settings = Settings.get()
    private val feedService: FeedService by instance()
    private val voteService: VoteService by instance()
    private val seenService: SeenService by instance()
    private val singleShotService: SingleShotService by instance()
    private val inMemoryCacheService: InMemoryCacheService by instance()
    private val userService: UserService by instance()
    private val downloadService: DownloadService by instance()
    private val favedCommentService: FavedCommentService by instance()
    private val configService: ConfigService by instance()

    private val swipeRefreshLayout: SwipeRefreshLayout by bindView(R.id.refresh)
    private val playerContainer: ViewGroup by bindView(player_container)
    private val content: RecyclerView by bindView(R.id.post_content)
    private val voteAnimationIndicator: Pr0grammIconView by bindView(R.id.vote_indicator)
    private val repostHint: View by bindView(R.id.repost_hint)

    private val tabletLayoutView: View? by bindOptionalView(R.id.tabletlayout)

    private lateinit var viewer: MediaView
    private lateinit var mediaControlsContainer: ViewGroup
    private lateinit var infoLineView: InfoLineView

    private var playerPlaceholder: FrameLayout? = null

    init {
        arguments = Bundle()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        if (savedInstanceState != null) {
            val tags = Parceler.get(TagListParceler::class.java, savedInstanceState, "PostFragment.tags")
            if (tags != null) {
                this.tags = tags
            }

            val comments = Parceler.get(CommentListParceler::class.java, savedInstanceState, "PostFragment.comments")
            if (comments != null) {
                this.comments = comments
            }
        }

        activeState().compose(bindToLifecycle()).subscribe { active ->
            // TODO shouldn't this be in onViewCreated or somewhere?
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

        adminMode = userService.userIsAdmin

        // check if we are admin or not
        userService.loginState().observeOn(mainThread()).compose(bindToLifecycle()).subscribe {
            adminMode = it.admin
            activity?.invalidateOptionsMenu()
        }
    }

    private fun stopMediaOnViewer() {
        viewer.stopMedia()

        val remoteMediaClient = CastContext.getSharedInstance(context)
                .sessionManager
                .currentCastSession
                ?.remoteMediaClient

        logger.info("Stopping media on remote client: {}", remoteMediaClient)
        remoteMediaClient?.stop()
    }

    private fun playMediaOnViewer() {
        val remoteMediaClient = CastContext.getSharedInstance(context)
                .sessionManager
                .currentCastSession?.remoteMediaClient


        if (remoteMediaClient == null) {
            logger.info("Playing media locally")
            viewer.playMedia()
            return
        }

        logger.info("Got cast remote client at {}", remoteMediaClient)

        // stop any local playing video
        viewer.stopMedia()

        val uris = UriHelper.of(context)

        val mediaUri = uris.media(feedItem, false).toString()
        val thumbUri = uris.thumbnail(feedItem)
        val contentType = ShareProvider.Companion.guessMimetype(context, feedItem)

        logger.info("Creating media metadata")
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC)
        meta.putString(MediaMetadata.KEY_TITLE, feedItem.user)
        meta.putString(MediaMetadata.KEY_SUBTITLE, "%s, %d up, %d down".format(
                formatTimeTo(context, feedItem.created, TimeMode.SINCE),
                feedItem.up, feedItem.down))

        // we already know the size
        meta.putInt(MediaMetadata.KEY_WIDTH, feedItem.width)
        meta.putInt(MediaMetadata.KEY_HEIGHT, feedItem.height)

        // add the thumbnail
        meta.addImage(WebImage(thumbUri))

        logger.info("Create media info for url {} ({})", mediaUri, contentType)
        val mediaInfo = MediaInfo.Builder(mediaUri)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(contentType)
                .setMetadata(meta)
                .build()

        remoteMediaClient.load(mediaInfo)
    }

    private fun activeState(): Observable<Boolean> {
        val startStopLifecycle = lifecycle().filter { ev ->
            ev == FragmentEvent.START || ev == FragmentEvent.STOP
        }

        // now combine with the activeStateSubject and return a new observable with
        // the "active state".
        val combined = combineLatest(startStopLifecycle, activeStateSubject) { ev, active ->
            active && ev == FragmentEvent.START
        }

        return combined.distinctUntilChanged()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_post, container, false) as ViewGroup
        addWarnOverlayIfNecessary(inflater, view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (activity !is ToolbarActivity) {
            throw IllegalStateException("Fragment must be child of a ToolbarActivity.")
        }

        (activity as ToolbarActivity).scrollHideToolbarListener.reset()

        val abHeight = AndroidUtility.getActionBarContentOffset(activity)
        if (tabletLayoutView != null) {
            tabletLayoutView!!.setPadding(0, abHeight, 0, 0)
            scrollHandler = NoopOnScrollListener()
        } else {
            // use height of the toolbar to configure swipe refresh layout.
            swipeRefreshLayout.setProgressViewOffset(false, 0, (1.5 * abHeight).toInt())
            scrollHandler = ScrollHandler()
        }

        content.addOnScrollListener(scrollHandler)

        swipeRefreshLayout.setColorSchemeResources(ThemeHelper.accentColor)
        swipeRefreshLayout.setOnRefreshListener {
            if (!isVideoFullScreen) {
                rewindOnLoad = true
                loadPostDetails()
            }
        }

        swipeRefreshLayout.keepScreenOn = settings.keepScreenOn

        content.itemAnimator = null
        content.layoutManager = LinearLayoutManager(getActivity())
        content.adapter = adapter

        initializeMediaView()
        initializeInfoLine()
        initializeCommentPostLine()

        commentsAdapter = CommentsAdapter(adminMode, userService.name ?: "")
        commentsAdapter.commentActionListener = this
        adapter.addAdapter(commentsAdapter)

        // apply login state.
        userService.loginState()
                .map { it.authorized }
                .observeOn(mainThread())
                .compose(bindToLifecycle())
                .subscribe { commentsAdapter.showFavCommentButton = it }


        // restore the postInfo, if possible.
        tags.takeIf { it.isNotEmpty() }?.let { displayTags(it) }
        comments.takeIf { it.isNotEmpty() }?.let { displayComments(it) }

        loadPostDetails()

        // show the repost badge if this is a repost
        repostHint.visible = inMemoryCacheService.isRepost(feedItem)
    }

    override fun onDestroyView() {
        content.removeOnScrollListener(scrollHandler)

        // restore orientation if the user closes this view
        Screen.unlockOrientation(activity)

        adapter.clear()

        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (tags.isNotEmpty()) {
            outState.putParcelable("PostFragment.tags", TagListParceler(tags))
        }

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

    private fun initializeCommentPostLine() {
        val line = CommentPostLine(activity)
        line.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        adapter.addAdapter(SingleViewAdapter.ofView(line))

        line.setCommentDraft(arguments.getString(ARG_COMMENT_DRAFT, ""))
        line.textChanges().subscribe({ text -> arguments.putString(ARG_COMMENT_DRAFT, text) })

        line.comments().subscribe { text ->
            val action = Runnable {
                line.clear()
                writeComment(text)
            }

            doIfAuthorizedHelper.run(action, action)
        }
    }

    private fun writeComment(text: String) {
        voteService.postComment(feedItem, 0, text)
                .compose(bindToLifecycleAsync())
                .lift(BusyDialog.busyDialog(activity))
                .subscribeWithErrorHandling { onNewComments(it) }

        AndroidUtility.hideSoftKeyboard(view)
    }

    /**
     * Scroll the th given comment

     * @param commentId The comment id to scroll to
     */
    private fun scrollToComment(commentId: Long) {
        adapter.getOffset(commentsAdapter)?.let { offset ->
            for (idx in 0..commentsAdapter.itemCount - 1) {
                if (commentsAdapter.getItemId(idx) == commentId) {
                    content.scrollToPosition(offset + idx)
                    break
                }
            }
        }

        commentsAdapter.selectedCommentId = commentId
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_post, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val isImage = isStaticImage(feedItem)

        menu.findItem(R.id.action_refresh)
                ?.isVisible = settings.showRefreshButton && !isVideoFullScreen

        menu.findItem(R.id.action_zoom)
                ?.isVisible = !isVideoFullScreen

        menu.findItem(R.id.action_share_image)
                ?.isVisible = true

        menu.findItem(R.id.action_search_image)
                ?.isVisible = isImage && settings.showGoogleImageButton

        menu.findItem(R.id.action_delete_item)
                ?.isVisible = adminMode

        menu.findItem(R.id.action_tags_details)
                ?.isVisible = adminMode
    }

    @OnOptionsItemSelected(R.id.action_zoom)
    fun enterFullscreen() {
        val activity = activity ?: return

        if (isStaticImage(feedItem)) {
            val intent = ZoomViewActivity.newIntent(activity, feedItem)
            startActivity(intent)

        } else {
            val params = ViewerFullscreenParameters.forViewer(getActivity(), viewer, settings.rotateInFullscreen)

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

            mediaControlsContainer.removeFromParent()
            viewer.addView(mediaControlsContainer)

            if (activity is AdControl) {
                activity.showAds(false)
            }
        }
    }

    private fun realignFullScreen() {
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

        AndroidUtility.applyWindowFullscreen(activity, false)


        fullscreenAnimator?.cancel()
        fullscreenAnimator = null

        infoLineView.visible = true
        swipeRefreshLayout.visible = true

        // reset the values correctly
        viewer.apply {
            rotation = 0f
            scaleX = 1f
            scaleY = 1f
            translationX = 0f
        }

        // simulate scrolling to fix the clipping and translationY
        simulateScroll()

        // go back to normal!
        val activity = activity
        activity.supportInvalidateOptionsMenu()

        if (activity is ToolbarActivity) {
            // show the toolbar again
            activity.scrollHideToolbarListener.reset()
        }

        if (activity is AdControl) {
            activity.showAds(true)
        }

        Screen.unlockOrientation(activity)

        // move views back
        mediaControlsContainer.removeFromParent()

        val targetView = if ((tabletLayoutView != null)) playerContainer else playerPlaceholder
        targetView?.addView(mediaControlsContainer)
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
        when (item.itemId) {
            R.id.action_search_image -> {
                ShareHelper.searchImage(activity, feedItem)
                return true
            }

            R.id.action_share_post -> {
                ShareHelper.sharePost(activity, feedItem)
                return true
            }

            R.id.action_share_direct_link -> {
                ShareHelper.shareDirectLink(activity, feedItem)
                return true
            }

            R.id.action_share_image -> {
                ShareHelper.shareImage(activity, feedItem)
                return true
            }

            R.id.action_copy_link -> {
                ShareHelper.copyLink(context, feedItem)
                return true
            }

            else -> return OptionMenuHelper.dispatch(this, item) || super.onOptionsItemSelected(item)
        }
    }

    @OnOptionsItemSelected(R.id.action_refresh)
    fun refreshWithIndicator() {
        if (swipeRefreshLayout.isRefreshing || isDetached)
            return

        rewindOnLoad = true
        swipeRefreshLayout.isRefreshing = true
        swipeRefreshLayout.postDelayed({ this.loadPostDetails() }, 500)
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
                .compose(bindToLifecycleAsync())
                .subscribeBy(onError = { err ->
                    if (err is DownloadService.CouldNotCreateDownloadDirectoryException) {
                        showErrorString(fragmentManager, getString(R.string.error_could_not_create_download_directory))
                    }
                })
    }

    override fun onStart() {
        super.onStart()

        favedCommentService.favedCommentIds
                .observeOn(mainThread())
                .compose(bindToLifecycle())
                .subscribe { commentsAdapter.favedComments = it }
    }

    override fun onResume() {
        super.onResume()

        // track that the user visited this post.
        if (configService.config().trackItemView) {
            Track.screen("Item")
        }
    }

    /**
     * Loads the information about the post. This includes the
     * tags and the comments.
     */
    private fun loadPostDetails() {
        feedService.post(feedItem.id())
                .compose(bindUntilEventAsync(FragmentEvent.DESTROY_VIEW))
                .subscribeWithErrorHandling { onPostReceived(it) }
    }

    private fun initializeInfoLine() {
        // get the vote from the service
        val cachedVote = voteService.getVote(feedItem).compose(bindToLifecycleAsync())


        infoLineView = view!!.findOptional<InfoLineView>(R.id.infoview)
                .or {
                    val fallback = InfoLineView(activity)
                    adapter.addAdapter(SingleViewAdapter.ofView(fallback))
                    fallback
                }
                .also { infoView ->
                    val isSelfPost = userService.name.equals(feedItem.user, ignoreCase = true)

                    // display the feed item in the view
                    infoView.setFeedItem(feedItem, isSelfPost, cachedVote)

                    infoView.onDetailClickedListener = this

                    // register the vote listener
                    infoView.onVoteListener = { vote ->
                        val action = Runnable {
                            showPostVoteAnimation(vote)

                            voteService.vote(feedItem, vote)
                                    .decoupleSubscribe()
                                    .compose(bindToLifecycleAsync<Any>())
                                    .subscribeWithErrorHandling()
                        }

                        val retry = Runnable { infoView.voteView.vote = vote }
                        doIfAuthorizedHelper.run(action, retry)
                    }

                    // and a vote listener vor voting tags.
                    infoView.tagVoteListener = { tag, vote ->
                        val action = Runnable {
                            voteService.vote(tag, vote)
                                    .decoupleSubscribe()
                                    .compose(bindToLifecycleAsync<Any>())
                                    .doAfterTerminate({ infoView.addVote(tag, vote) })
                                    .subscribeWithErrorHandling()
                        }

                        doIfAuthorizedHelper.run(action, action)
                    }

                    infoView.onAddTagClickedListener = {
                        doIfAuthorizedHelper.run {
                            val dialog = NewTagDialogFragment()
                            dialog.show(childFragmentManager, null)
                        }
                    }
                }
    }

    @OnOptionsItemSelected(R.id.action_delete_item)
    fun showDeleteItemDialog() {
        val dialog = ItemUserAdminDialog.forItem(feedItem)
        dialog.show(fragmentManager, null)
    }

    @OnOptionsItemSelected(R.id.action_tags_details)
    fun showTagsDetailsDialog() {
        val dialog = TagsDetailsDialog.newInstance(feedItem.id())
        dialog.show(fragmentManager, null)
    }

    private fun showPostVoteAnimation(vote: Vote?) {
        if (vote == null || vote === Vote.NEUTRAL)
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

    private fun initializeMediaView() {
        val padding = AndroidUtility.getActionBarContentOffset(activity)

        // initialize a new viewer fragment
        var uri = MediaUri.of(context, feedItem)
        if (!uri.isLocal && AndroidUtility.isOnMobile(activity)) {
            val confirmOnMobile = settings.confirmPlayOnMobile
            if (confirmOnMobile === Settings.ConfirmOnMobile.ALL) {
                uri = uri.withDelay(true)

            } else if (confirmOnMobile === Settings.ConfirmOnMobile.VIDEO && uri.mediaType !== MediaUri.MediaType.IMAGE) {

                uri = uri.withDelay(true)
            }
        }

        viewer = MediaViews.newInstance(MediaView.Config.of(activity, uri)
                .copy(audio = feedItem.audio, previewInfo = previewInfo))

        viewer.viewed().observeOn(BackgroundScheduler.instance()).subscribe {
            //  mark this item seen. We do that in a background thread
            seenService.markAsSeen(feedItem.id())
        }

        // inform viewer over fragment lifecycle events!
        MediaViews.adaptFragmentLifecycle(lifecycle(), viewer)

        registerTapListener(viewer)

        // add views in the correct order (normally first child)
        val idx = playerContainer.indexOfChild(voteAnimationIndicator)
        playerContainer.addView(viewer, idx)

        // Add a contrainer for the children
        mediaControlsContainer = FrameLayout(context)
        mediaControlsContainer.layoutParams = FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)


        if (tabletLayoutView != null) {
            viewer.layoutParams = FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER)

            playerContainer.addView(mediaControlsContainer)

            if (isStaticImage(feedItem)) {
                viewer.thumbnail()
                        .compose(bindUntilEvent(FragmentEvent.DESTROY_VIEW))
                        .subscribe { this.updatePlayerContainerBackground(it) }
            } else {
                playerContainer.setBackgroundColor(Color.BLACK)
            }

        } else {
            viewer.setPadding(0, padding, 0, 0)

            // we add a placeholder to the first element of the recycler view.
            // this placeholder will mirror the size of the viewer.
            val placeholder = PlaceholderView()
            adapter.addAdapter(SingleViewAdapter.ofView(placeholder))

            RxView.layoutChanges(viewer).subscribe {
                val newHeight = viewer.measuredHeight
                if (newHeight != placeholder.fixedHeight) {
                    placeholder.fixedHeight = newHeight

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        placeholder.requestLayout()
                    } else {
                        // it looks like a requestLayout is not honored on pre kitkat devices
                        // if already in a layout pass.
                        placeholder.post { placeholder.requestLayout() }
                    }

                    if (isVideoFullScreen) {
                        realignFullScreen()
                    }
                }
            }

            RxView.layoutChanges(placeholder).subscribe {
                // simulate scroll after layouting the placeholder to
                // reflect changes to the viewers clipping.
                simulateScroll()
            }


            placeholder.addView(mediaControlsContainer)

            playerPlaceholder = placeholder
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

    private fun updatePlayerContainerBackground(thumbnail: Bitmap) {
        val palette = Observable.fromCallable {
            Palette.Builder(thumbnail).run {
                clearTargets()
                addTarget(DARK_MUTED)
                generate()
            }
        }

        palette.compose(bindToLifecycleAsync()).subscribe {
            val color = it.getDarkMutedColor(0)
            playerContainer.setBackgroundColor(AndroidUtility.darken(color, 0.5f))
        }
    }

    private val previewInfo: PreviewInfo by lazy {
        val parent = parentFragment
        if (parent is PreviewInfoSource) {
            parent.previewInfoFor(feedItem)?.let { return@lazy it }
        }

        return@lazy PreviewInfo.of(context, feedItem)
    }

    private fun simulateScroll() {
        val handler = scrollHandler
        if (handler is ScrollHandler) {
            handler.onScrolled(content, 0, 0)
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
            internal val isImage = isStaticImage(feedItem)

            override fun onSingleTap(event: MotionEvent): Boolean {
                if (isImage && settings.singleTapForFullscreen) {
                    enterFullscreen()
                }

                return true
            }

            override fun onDoubleTap(): Boolean {
                if (settings.doubleTapToUpvote) {
                    infoLineView.voteView.triggerUpVoteClicked()
                }

                return true
            }
        }
    }

    /**
     * Called with the downloaded post information.

     * @param post The post information that was downloaded.
     */
    private fun onPostReceived(post: Api.Post) {
        swipeRefreshLayout.isRefreshing = false

        // update from post
        displayTags(post.tags)
        displayComments(post.comments)

        if (rewindOnLoad) {
            rewindOnLoad = false
            viewer.rewind()
        }
    }

    private fun displayTags(tags_: List<Api.Tag>) {
        this.tags = inMemoryCacheService.enhanceTags(feedItem.id(), tags_).toList()

        // show tags now
        infoLineView.updateTags(tags.associate { it to Vote.NEUTRAL })

        // and update tags with votes later.
        voteService.getTagVotes(tags)
                .filter { votes -> !votes.isEmpty }
                .onErrorResumeNext(just(VoteService.NO_VOTES))
                .compose(bindToLifecycleAsync())
                .subscribe { votes ->
                    infoLineView.updateTags(tags.associate { it to (votes[it.id] ?: Vote.NEUTRAL) })
                }

        hideProgressIfLoop(tags)
    }

    /**
     * If the current post is a loop, we'll check if it is a loop. If it is,
     * we will hide the little video progress bar.
     */
    private fun hideProgressIfLoop(tags: List<Api.Tag>) {
        val actualView = viewer.actualMediaView
        if (actualView is AbstractProgressMediaView) {
            if (tags.any { it.isLoopTag() }) {
                actualView.hideVideoProgress()
            }
        }
    }

    /**
     * Displays the given list of comments combined with the voting for those comments.

     * @param comments The list of comments to display.
     */
    private fun displayComments(comments: List<Api.Comment>) {
        this.comments = comments.toList()

        // show now
        commentsAdapter.set(this.comments, VoteService.NO_VOTES, feedItem.user)

        val commentId = arguments.getLong(ARG_AUTOSCROLL_COMMENT_ID, 0)
        if (commentId > 0) {
            scrollToComment(commentId)
        }

        // load the votes for the comments and update, when we found any
        voteService.getCommentVotes(this.comments)
                .filter { votes -> !votes.isEmpty }
                .onErrorResumeEmpty()
                .compose(bindToLifecycleAsync())
                .subscribe { votes -> commentsAdapter.set(this.comments, votes, feedItem.user) }
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
        voteService.tag(feedItem.id, tags.filter { tag -> isValidTag(tag) })
                .compose(bindToLifecycleAsync())
                .lift(BusyDialog.busyDialog(activity))
                .subscribeWithErrorHandling { displayTags(it) }
    }

    override fun onCommentVoteClicked(comment: Api.Comment, vote: Vote): Boolean {
        return doIfAuthorizedHelper.run(Runnable {
            voteService.vote(comment, vote)
                    .decoupleSubscribe()
                    .compose(bindToLifecycleAsync<Any>())
                    .subscribeWithErrorHandling()
        })
    }

    override fun onAnswerClicked(comment: Api.Comment) {
        val retry = Runnable { onAnswerClicked(comment) }

        doIfAuthorizedHelper.run(Runnable {
            startActivityForResult(
                    WriteMessageActivity.answerToComment(activity, feedItem, comment),
                    RequestCodes.WRITE_COMMENT)

        }, retry)
    }

    override fun onCommentAuthorClicked(comment: Api.Comment) {
        onUserClicked(comment.name)
    }

    override fun onCommentMarkAsFavoriteClicked(comment: Api.Comment, markAsFavorite: Boolean) {
        val operationCompletable: Completable
        if (markAsFavorite) {
            operationCompletable = favedCommentService.save(feedItem, comment)
        } else {
            operationCompletable = favedCommentService.delete(comment.id)
        }

        operationCompletable
                .decoupleSubscribe()
                .compose(bindUntilEventAsync(FragmentEvent.DESTROY_VIEW))
                .subscribeWithErrorHandling()

        if (singleShotService.isFirstTime("kfav-userscript-hint")) {
            showDialog(context) {
                content(R.string.hint_kfav_userscript)
                positive(R.string.open_website) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://goo.gl/py7xNW"))
                    context?.startActivity(intent)
                }

                negative(R.string.ignore)
            }
        }
    }

    override fun onCopyCommentLink(comment: Api.Comment) {
        ShareHelper.copyLink(context, feedItem, comment)
    }

    override fun onDeleteCommentClicked(comment: Api.Comment): Boolean {
        if (adminMode) {
            val dialog = DeleteCommentDialog.newInstance(comment.id)
            dialog.show(fragmentManager, null)
            return true

        } else {
            return false
        }
    }

    override fun onTagClicked(tag: Api.Tag) {
        (parentFragment as PostPagerFragment).onTagClicked(tag)
    }

    override fun onUserClicked(username: String) {
        (parentFragment as PostPagerFragment).onUsernameClicked(username)
    }

    private fun onNewComments(response: Api.NewComment) {
        autoScrollToComment(response.commentId)
        displayComments(response.comments)

        Snackbar.make(content, R.string.comment_written_successful, Snackbar.LENGTH_LONG).show()
    }

    fun autoScrollToComment(commentId: Long) {
        arguments.putLong(ARG_AUTOSCROLL_COMMENT_ID, commentId)
        scrollToComment(commentId)
    }

    fun mediaHorizontalOffset(offset: Int) {
        viewer.translationX = offset.toFloat()
    }

    override fun onBackButton(): Boolean {
        if (isVideoFullScreen) {
            exitFullscreen()
            return true
        }

        return false
    }

    private inner class PlaceholderView : FrameLayout(this@PostFragment.context) {
        var fixedHeight = AndroidUtility.dp(activity, 150)

        init {
            val v = View(context)
            v.setBackgroundResource(R.drawable.dropshadow_reverse)

            val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 8))
            lp.gravity = Gravity.BOTTOM
            v.layoutParams = lp

            addView(v)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(width, fixedHeight)

            measureChildren(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(fixedHeight, MeasureSpec.EXACTLY))
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            return viewer.onTouchEvent(event)
        }
    }

    private inner class ScrollHandler : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (isVideoFullScreen)
                return

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
    }
}
