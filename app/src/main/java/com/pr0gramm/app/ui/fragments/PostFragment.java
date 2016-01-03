package com.pr0gramm.app.ui.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentActivity;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.akodiakson.sdk.simple.Sdk;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.RequestCodes;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.api.pr0gramm.response.Comment;
import com.pr0gramm.app.api.pr0gramm.response.NewComment;
import com.pr0gramm.app.api.pr0gramm.response.Post;
import com.pr0gramm.app.api.pr0gramm.response.Tag;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedService;
import com.pr0gramm.app.feed.Vote;
import com.pr0gramm.app.parcel.CommentListParceler;
import com.pr0gramm.app.parcel.TagListParceler;
import com.pr0gramm.app.parcel.core.Parceler;
import com.pr0gramm.app.services.DownloadService;
import com.pr0gramm.app.services.FavedCommentService;
import com.pr0gramm.app.services.ImmutableFavedComment;
import com.pr0gramm.app.services.LocalCacheService;
import com.pr0gramm.app.services.SeenService;
import com.pr0gramm.app.services.ShareHelper;
import com.pr0gramm.app.services.ShareProvider;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.services.UriHelper;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.services.VoteService;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.MainActivity;
import com.pr0gramm.app.ui.MergeRecyclerAdapter;
import com.pr0gramm.app.ui.OnOptionsItemSelected;
import com.pr0gramm.app.ui.OptionMenuHelper;
import com.pr0gramm.app.ui.PermissionHelperActivity;
import com.pr0gramm.app.ui.PreviewInfo;
import com.pr0gramm.app.ui.Screen;
import com.pr0gramm.app.ui.ScrollHideToolbarListener;
import com.pr0gramm.app.ui.SingleViewAdapter;
import com.pr0gramm.app.ui.WriteMessageActivity;
import com.pr0gramm.app.ui.ZoomViewActivity;
import com.pr0gramm.app.ui.base.BaseFragment;
import com.pr0gramm.app.ui.dialogs.LoginActivity;
import com.pr0gramm.app.ui.dialogs.NewTagDialogFragment;
import com.pr0gramm.app.ui.views.CommentPostLine;
import com.pr0gramm.app.ui.views.CommentsAdapter;
import com.pr0gramm.app.ui.views.InfoLineView;
import com.pr0gramm.app.ui.views.viewer.AbstractProgressMediaView;
import com.pr0gramm.app.ui.views.viewer.MediaUri;
import com.pr0gramm.app.ui.views.viewer.MediaView;
import com.pr0gramm.app.ui.views.viewer.MediaViews;
import com.pr0gramm.app.util.AndroidUtility;
import com.trello.rxlifecycle.FragmentEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Actions;
import rx.subjects.BehaviorSubject;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.animation.PropertyValuesHolder.ofFloat;
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Maps.toMap;
import static com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity;
import static com.pr0gramm.app.ui.ScrollHideToolbarListener.estimateRecyclerViewScrollY;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.showErrorString;
import static com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog;
import static java.util.Collections.emptyMap;
import static rx.Observable.combineLatest;
import static rx.Observable.empty;

/**
 * This fragment shows the content of one post.
 */
public class PostFragment extends BaseFragment implements
        NewTagDialogFragment.OnAddNewTagsListener,
        CommentsAdapter.CommentActionListener, InfoLineView.OnDetailClickedListener {

    private static final String ARG_FEED_ITEM = "PostFragment.post";
    private static final String ARG_COMMENT_DRAFT = "PostFragment.comment-draft";

    private FeedItem feedItem;
    private MediaView viewer;


    @Inject
    FeedService feedService;

    @Inject
    VoteService voteService;

    @Inject
    Settings settings;

    @Inject
    SeenService seenService;

    @Inject
    DownloadManager downloadManager;

    @Inject
    SingleShotService singleShotService;

    @Inject
    LocalCacheService localCacheService;

    @Inject
    UserService userService;

    @Inject
    DownloadService downloadService;

    @Inject
    FavedCommentService favedCommentService;

    @Bind(R.id.refresh)
    SwipeRefreshLayout swipeRefreshLayout;

    @Bind(R.id.player_container)
    ViewGroup playerContainer;

    @Bind(R.id.post_content)
    RecyclerView content;

    @Bind(R.id.vote_indicator)
    TextView voteAnimationIndicator;

    @Bind(R.id.repost_hint)
    View repostHint;

    private InfoLineView infoLineView;

    // start with an empty adapter here
    private MergeRecyclerAdapter adapter;
    private CommentsAdapter commentsAdapter;

    private Optional<Long> autoScrollTo;
    private RecyclerView.OnScrollListener scrollHandler;

    private final LoginActivity.DoIfAuthorizedHelper doIfAuthorizedHelper = LoginActivity.helper(this);
    private PreviewInfo previewInfo;

    private List<Tag> tags;
    private List<Comment> comments;
    private boolean rewindOnLoad;
    private boolean tabletLayout;

    private final BehaviorSubject<Boolean> activeStateSubject = BehaviorSubject.create(false);

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setHasOptionsMenu(true);

        // get the item that is to be displayed.
        feedItem = getArguments().getParcelable(ARG_FEED_ITEM);

        if (savedState != null) {
            tags = Parceler.get(TagListParceler.class, savedState, "PostFragment.tags");
            comments = Parceler.get(CommentListParceler.class, savedState, "PostFragment.comments");
        }

        autoScrollTo = Optional.absent();

        activeState().compose(bindToLifecycleForeground()).subscribe(active -> {
            if (viewer != null) {
                if (active) {
                    viewer.playMedia();
                } else {
                    viewer.stopMedia();
                }
            }

            if (!active) {
                exitFullscreenAnimated(false);
            }
        });
    }

    private Observable<Boolean> activeState() {
        Observable<FragmentEvent> startStopLifecycle = lifecycle()
                .filter(ev -> ev == FragmentEvent.START || ev == FragmentEvent.STOP);

        // now combine with the activeStateSubject and return a new observable with
        // the "active state".
        Observable<Boolean> combined = combineLatest(startStopLifecycle, activeStateSubject,
                (ev, active) -> active && ev == FragmentEvent.START);

        return combined.distinctUntilChanged();
    }

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        ViewGroup view = (ViewGroup) inflater.inflate(R.layout.fragment_post, container, false);
        addWarnOverlayIfNecessary(inflater, view);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.tabletLayout = view.findViewById(R.id.infoview) != null;

        if (!(getActivity() instanceof ToolbarActivity)) {
            throw new IllegalStateException("Fragment must be child of a ToolbarActivity.");
        }

        ToolbarActivity activity = (ToolbarActivity) getActivity();
        activity.getScrollHideToolbarListener().reset();

        int abHeight = AndroidUtility.getActionBarContentOffset(getActivity());
        if (tabletLayout) {
            View tabletLayout = ButterKnife.findById(view, R.id.tabletlayout);
            tabletLayout.setPadding(0, abHeight, 0, 0);

            ((FrameLayout.LayoutParams) voteAnimationIndicator.getLayoutParams()).gravity
                    = Gravity.CENTER;

            scrollHandler = new NoopOnScrollListener();
        } else {
            // use height of the toolbar to configure swipe refresh layout.
            swipeRefreshLayout.setProgressViewOffset(false, 0, (int) (1.5 * abHeight));

            scrollHandler = new ScrollHandler(activity);
        }

        content.addOnScrollListener(scrollHandler);

        swipeRefreshLayout.setColorSchemeResources(R.color.primary);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (!isVideoFullScreen()) {
                rewindOnLoad = true;
                loadPostDetails();
            }
        });


        swipeRefreshLayout.setKeepScreenOn(settings.keepScreenOn());

        adapter = new MergeRecyclerAdapter();
        content.setItemAnimator(null);
        content.setLayoutManager(new LinearLayoutManager(getActivity()));
        content.setAdapter(adapter);

        initializeMediaView();
        initializeInfoLine();
        initializeCommentPostLine();

        commentsAdapter = new CommentsAdapter(userService.getName().or(""));
        commentsAdapter.setCommentActionListener(this);
        commentsAdapter.setPrioritizeOpComments(settings.prioritizeOpComments());
        commentsAdapter.setShowFavCommentButton(userService.isAuthorized());
        adapter.addAdapter(commentsAdapter);

        // restore the postInfo, if possible.
        if (tags != null && comments != null) {
            displayTags(tags);
            displayComments(comments);
        }

        loadPostDetails();

        // show the repost badge if this is a repost
        repostHint.setVisibility(isRepost() ? View.VISIBLE : View.GONE);
    }

    private boolean isRepost() {
        return localCacheService.isRepost(feedItem);
    }

    @Override
    public void onDestroyView() {
        content.removeOnScrollListener(scrollHandler);

        // restore orientation if the user closes this view
        Screen.unlockOrientation(getActivity());

        adapter = null;

        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("PostFragment.tags", new TagListParceler(tags));
        outState.putParcelable("PostFragment.comments", new CommentListParceler(comments));
    }

    private void addWarnOverlayIfNecessary(LayoutInflater inflater, ViewGroup view) {
        // add a view over the main view, if the post is not visible now
        if (!settings.getContentType().contains(feedItem.getContentType())) {
            View overlay = inflater.inflate(R.layout.warn_post_can_not_be_viewed, view, false);
            view.addView(overlay);

            // link the hide button
            View button = overlay.findViewById(R.id.hide_warning_button);
            button.setOnClickListener(v -> AndroidUtility.removeView(overlay));
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        doIfAuthorizedHelper.onActivityResult(requestCode, resultCode);

        if (requestCode == RequestCodes.WRITE_COMMENT && resultCode == Activity.RESULT_OK) {
            onNewComments(WriteMessageActivity.getNewComment(data));
        }
    }

    public void setPreviewInfo(PreviewInfo previewInfo) {
        this.previewInfo = previewInfo;
    }

    private void initializeCommentPostLine() {
        ViewGroup.MarginLayoutParams layoutParams = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        CommentPostLine line = new CommentPostLine(getActivity());
        line.setLayoutParams(layoutParams);
        adapter.addAdapter(SingleViewAdapter.ofView(line));

        line.setCommentDraft(getArguments().getString(ARG_COMMENT_DRAFT, ""));
        line.textChanges().subscribe(text -> getArguments().putString(ARG_COMMENT_DRAFT, text));

        line.comments().subscribe(text -> {
            Runnable action = () -> {
                line.clear();
                writeComment(text);
            };

            doIfAuthorizedHelper.run(action, action);
        });
    }

    private void writeComment(String text) {
        voteService.postComment(feedItem, 0, text)
                .compose(bindToLifecycle())
                .lift(busyDialog(this))
                .subscribe(this::onNewComments, defaultOnError());
    }

    /**
     * Scroll the th given comment
     *
     * @param commentId The comment id to scroll to
     */
    private void scrollToComment(long commentId) {
        Optional<Integer> offset = adapter.getOffset(commentsAdapter);
        if (!offset.isPresent())
            return;

        for (int idx = 0; idx < commentsAdapter.getItemCount(); idx++) {
            if (commentsAdapter.getItemId(idx) == commentId) {
                content.scrollToPosition(offset.get() + idx);
                break;
            }
        }

        commentsAdapter.setSelectedCommentId(commentId);
    }

    public void autoScrollToComment(long commentId) {
        if (commentId > 0) {
            autoScrollTo = Optional.of(commentId);
        } else {
            autoScrollTo = Optional.absent();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_post, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        boolean isImage = isStaticImage(feedItem);
        boolean isRotated = getActivity().getWindowManager().getDefaultDisplay().getRotation() != Surface.ROTATION_0;

        MenuItem item;
        if ((item = menu.findItem(R.id.action_refresh)) != null)
            item.setVisible(settings.showRefreshButton() && !isVideoFullScreen());

        if ((item = menu.findItem(R.id.action_zoom)) != null)
            item.setVisible(!isVideoFullScreen() && (isImage || !isRotated));

        if ((item = menu.findItem(R.id.action_share_image)) != null)
            item.setVisible(ShareProvider.canShare(getActivity(), feedItem));

        if ((item = menu.findItem(R.id.action_search_image)) != null)
            item.setVisible(isImage && settings.showGoogleImageButton());
    }

    @OnOptionsItemSelected(R.id.action_zoom)
    public void enterFullscreen() {
        FragmentActivity activity = getActivity();
        if (activity == null)
            return;

        if (isStaticImage(feedItem)) {
            boolean hq = settings.loadHqInZoomView();
            Intent intent = ZoomViewActivity.newIntent(activity, feedItem, hq);
            startActivity(intent);

        } else {
            FullscreenParams params = new FullscreenParams();

            ObjectAnimator.ofPropertyValuesHolder(viewer,
                    ofFloat(View.ROTATION, params.rotation),
                    ofFloat(View.TRANSLATION_Y, params.trY),
                    ofFloat(View.SCALE_X, params.scale),
                    ofFloat(View.SCALE_Y, params.scale))
                    .setDuration(500)
                    .start();

            repostHint.setVisibility(View.GONE);

            // hide content below
            swipeRefreshLayout.setVisibility(View.GONE);

            if (activity instanceof ToolbarActivity) {
                // hide the toolbar if required necessary
                ((ToolbarActivity) activity).getScrollHideToolbarListener().hide();
            }

            viewer.setClipBoundsCompat(null);

            activity.supportInvalidateOptionsMenu();
            registerExitFullscreenListener();

            // forbid orientation changes while in fullscreen
            Screen.lockOrientation(activity);
        }
    }

    private void realignFullScreen() {
        FullscreenParams params = new FullscreenParams();

        viewer.setTranslationY(params.trY);
        viewer.setScaleX(params.scale);
        viewer.setScaleY(params.scale);
    }

    private void registerExitFullscreenListener() {
        // add a listener to show/hide the fullscreen.
        View view = getView();
        if (view != null) {
            // get the focus for the back button
            view.setFocusableInTouchMode(true);
            view.requestFocus();

            view.setOnKeyListener((v, keyCode, event) -> {
                if (isVideoFullScreen()) {
                    if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                        // remove listener
                        view.setOnKeyListener(null);
                        view.setOnFocusChangeListener(null);

                        // and move back to normal state
                        exitFullscreen();
                        return true;
                    }
                }

                return false;
            });

            view.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && isVideoFullScreen()) {
                    view.requestFocus();
                }
            });
        }
    }

    public void exitFullscreen() {
        exitFullscreenAnimated(true);
    }

    private void exitFullscreenAnimated(boolean animated) {
        if (!isVideoFullScreen())
            return;

        swipeRefreshLayout.setVisibility(View.VISIBLE);

        // reset the values correctly
        viewer.setRotation(0.f);
        viewer.setScaleX(1.f);
        viewer.setScaleY(1.f);
        viewer.setTranslationX(0.f);

        // simulate scrolling to fix the clipping and translationY
        simulateScroll();

        // go back to normal!
        FragmentActivity activity = getActivity();
        activity.supportInvalidateOptionsMenu();

        if (activity instanceof ToolbarActivity) {
            // show the toolbar again
            ((ToolbarActivity) activity).getScrollHideToolbarListener().reset();
        }

        Screen.unlockOrientation(activity);
    }

    private boolean isVideoFullScreen() {
        return swipeRefreshLayout != null && swipeRefreshLayout.getVisibility() != View.VISIBLE;
    }

    @OnOptionsItemSelected(MainActivity.ID_FAKE_HOME)
    public boolean onHomePressed() {
        if (isVideoFullScreen()) {
            exitFullscreen();
            return true;
        }

        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_search_image:
                ShareHelper.searchImage(getActivity(), feedItem);
                return true;

            case R.id.action_share_post:
                ShareHelper.sharePost(getActivity(), feedItem);
                return true;

            case R.id.action_share_direct_link:
                ShareHelper.shareDirectLink(getActivity(), feedItem);
                return true;

            case R.id.action_share_image:
                ShareHelper.shareImage(getActivity(), feedItem);
                return true;

            default:
                return OptionMenuHelper.dispatch(this, item) || super.onOptionsItemSelected(item);
        }
    }

    @OnOptionsItemSelected(R.id.action_refresh)
    public void refreshWithIndicator() {
        if (swipeRefreshLayout.isRefreshing())
            return;

        rewindOnLoad = true;
        swipeRefreshLayout.setRefreshing(true);
        swipeRefreshLayout.postDelayed(this::loadPostDetails, 500);
    }

    @OnOptionsItemSelected(R.id.action_download)
    public void downloadPostMedia() {
        ((PermissionHelperActivity) getActivity())
                .requirePermission(WRITE_EXTERNAL_STORAGE)
                .compose(bindUntilEvent(FragmentEvent.DESTROY))
                .subscribe(ignored -> downloadPostWithPermissionGranted(), defaultOnError());
    }

    private void downloadPostWithPermissionGranted() {
        Optional<String> error = downloadService.download(feedItem);
        if (error.isPresent())
            showErrorString(getFragmentManager(), error.get());
    }

    @Override
    public void onStart() {
        super.onStart();

        favedCommentService.favedCommentIds()
                .compose(bindToLifecycle())
                .subscribe(commentsAdapter::setFavedComments);
    }

    @Override
    public void onResume() {
        super.onResume();

        // set ordering
        commentsAdapter.setPrioritizeOpComments(settings.prioritizeOpComments());
    }

    /**
     * Loads the information about the post. This includes the
     * tags and the comments.
     */
    private void loadPostDetails() {
        int delay = Sdk.isAtLeastKitKat() ? 500 : 100;

        feedService.loadPostDetails(feedItem.getId())
                .delay(delay, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .compose(bindUntilEvent(FragmentEvent.DESTROY_VIEW))
                .subscribe(this::onPostReceived, defaultOnError());
    }

    @SuppressWarnings("CodeBlock2Expr")
    private void initializeInfoLine() {
        // get the vote from the service
        Observable<Vote> cachedVote = voteService.getVote(feedItem).compose(bindToLifecycle());

        //noinspection ConstantConditions
        infoLineView = ButterKnife.findById(getView(), R.id.infoview);
        if (infoLineView == null) {
            infoLineView = new InfoLineView(getActivity());
            adapter.addAdapter(SingleViewAdapter.ofView(infoLineView));
        }

        boolean isSelfPost = userService.getName()
                .transform(name -> name.equalsIgnoreCase(feedItem.getUser()))
                .or(false);

        // display the feed item in the view
        infoLineView.setFeedItem(feedItem, isSelfPost, cachedVote);

        infoLineView.setOnDetailClickedListener(this);

        // register the vote listener
        infoLineView.setOnVoteListener(vote -> {
            Runnable action = () -> {
                showPostVoteAnimation(vote);

                voteService.vote(feedItem, vote)
                        .compose(bindToLifecycle())
                        .subscribe(Actions.empty(), defaultOnError());
            };

            Runnable retry = () -> infoLineView.getVoteView().setVote(vote);
            return doIfAuthorizedHelper.run(action, retry);
        });

        // and a vote listener vor voting tags.
        infoLineView.setTagVoteListener((tag, vote) -> {
            Runnable action = () -> {
                voteService.vote(tag, vote)
                        .compose(bindToLifecycle())
                        .subscribe(Actions.empty(), defaultOnError());
            };

            return doIfAuthorizedHelper.run(action, action);
        });

        infoLineView.setOnAddTagClickedListener(() -> {
            NewTagDialogFragment dialog = new NewTagDialogFragment();
            dialog.show(getChildFragmentManager(), null);
        });

    }

    private void showPostVoteAnimation(Vote vote) {
        if (vote == null || vote == Vote.NEUTRAL)
            return;

        // quickly center the vote button
        simulateScroll();

        String text = vote == Vote.UP ? "+" : (vote == Vote.DOWN ? "-" : "*");
        voteAnimationIndicator.setText(text);

        voteAnimationIndicator.setVisibility(View.VISIBLE);
        voteAnimationIndicator.setAlpha(0);
        voteAnimationIndicator.setScaleX(0.7f);
        voteAnimationIndicator.setScaleY(0.7f);

        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(voteAnimationIndicator,
                ofFloat(View.ALPHA, 0, 0.6f, 0.7f, 0.6f, 0),
                ofFloat(View.SCALE_X, 0.7f, 1.3f),
                ofFloat(View.SCALE_Y, 0.7f, 1.3f));

        animator.start();
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                View view = PostFragment.this.voteAnimationIndicator;
                if (view != null) {
                    view.setVisibility(View.GONE);
                }
            }
        });
    }

    private void initializeMediaView() {
        int padding = AndroidUtility.getActionBarContentOffset(getActivity());

        // initialize a new viewer fragment
        MediaUri uri = MediaUri.of(UriHelper.of(getContext()).media(feedItem));
        if (!uri.isLocal() && AndroidUtility.isOnMobile(getActivity())) {
            Settings.ConfirmOnMobile confirmOnMobile = settings.confirmPlayOnMobile(getContext());
            if (confirmOnMobile == Settings.ConfirmOnMobile.ALL) {
                uri = uri.withDelay(true);

            } else if (confirmOnMobile == Settings.ConfirmOnMobile.VIDEO
                    && uri.getMediaType() != MediaUri.MediaType.IMAGE) {

                uri = uri.withDelay(true);
            }
        }

        viewer = MediaViews.newInstance(getActivity(), uri, () -> {
            //  mark this item seen. We do that in a background thread
            seenService.markAsSeen(feedItem);
        });

        // inform viewer over fragment lifecycle events!
        MediaViews.adaptFragmentLifecycle(lifecycle(), viewer);

        registerTapListener(viewer);

        PreviewInfo previewInfo = this.previewInfo != null
                ? this.previewInfo : getPreviewInfoFromCache();

        if (previewInfo != null) {
            viewer.setPreviewImage(previewInfo, "TransitionTarget-" + feedItem.getId());
            if (Sdk.isAtLeastLollipop()) {
                viewer.postDelayed(this::onTransitionEnds, 350);
            } else {
                viewer.post(this::onTransitionEnds);
            }
        } else {
            viewer.post(this::onTransitionEnds);
        }

        // add views in the correct order
        int idx = playerContainer.indexOfChild(voteAnimationIndicator);
        playerContainer.addView(viewer, idx);

        if (tabletLayout) {
            viewer.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER));
        } else {
            PlaceholderView placeholder = new PlaceholderView();

            viewer.setPadding(0, padding, 0, 0);
            viewer.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                int newHeight = viewer.getMeasuredHeight();
                if (newHeight != placeholder.fixedHeight) {
                    placeholder.fixedHeight = newHeight;

                    if (Sdk.isAtLeastKitKat()) {
                        placeholder.requestLayout();
                    } else {
                        // it looks like a requestLayout is not honored on pre kitkat devices
                        // if already in a layout pass.
                        placeholder.post(placeholder::requestLayout);
                    }

                    if (isVideoFullScreen()) {
                        realignFullScreen();
                    }

                    simulateScroll();
                }
            });

            adapter.addAdapter(SingleViewAdapter.ofView(placeholder));
        }
    }

    private void onTransitionEnds() {
        if (viewer != null && content != null) {
            viewer.onTransitionEnds();
            simulateScroll();
        }
    }

    private void simulateScroll() {
        if (scrollHandler instanceof ScrollHandler) {
            scrollHandler.onScrolled(content, 0, 0);
        } else {
            // simulate a scroll to "null"
            offsetMediaView(true, 0.0f);
        }
    }

    /**
     * Registers a tap listener on the given viewer instance. The listener is used
     * to handle double-tap-to-vote events from the view.
     *
     * @param viewer The viewer to register the tap listener to.
     */
    private void registerTapListener(MediaView viewer) {
        viewer.setTapListener(new MediaView.TapListener() {
            final boolean isImage = isStaticImage(feedItem);

            @Override
            public boolean onSingleTap() {
                if (isImage && settings.singleTapForFullscreen()) {
                    enterFullscreen();
                }

                return true;
            }

            @Override
            public boolean onDoubleTap() {
                if (settings.doubleTapToUpvote()) {
                    infoLineView.getVoteView().triggerUpVoteClicked();
                }

                return true;
            }
        });
    }

    /**
     * Called with the downloaded post information.
     *
     * @param post The post information that was downloaded.
     */
    private void onPostReceived(Post post) {
        swipeRefreshLayout.setRefreshing(false);

        // update from post
        displayTags(post.getTags());
        displayComments(post.getComments());

        if (rewindOnLoad) {
            rewindOnLoad = false;
            viewer.rewind();
        }
    }

    private void displayTags(List<Tag> tags_) {
        List<Tag> tags = localCacheService.enhanceTags(feedItem.getId(), tags_);
        this.tags = ImmutableList.copyOf(tags);

        // show tags now
        infoLineView.setTags(toMap(tags, tag -> Vote.NEUTRAL));

        // and update tags with votes later.
        voteService.getTagVotes(tags)
                .filter(votes -> !votes.isEmpty())
                .onErrorResumeNext(Observable.<Map<Long, Vote>>empty())
                .compose(bindToLifecycle())
                .subscribe(votes -> infoLineView.setTags(toMap(tags,
                        tag -> firstNonNull(votes.get(tag.getId()), Vote.NEUTRAL))));

        hideProgressIfLoop(tags);
    }

    /**
     * If the current post is a loop, we'll check if it is a loop. If it is,
     * we will hide the little video progress bar.
     */
    private void hideProgressIfLoop(List<Tag> tags) {
        MediaView actualView = viewer != null ? viewer.getActualMediaView() : null;

        if (actualView instanceof AbstractProgressMediaView) {
            if (Iterables.any(tags, tag -> isLoopTag(tag.getTag()))) {
                ((AbstractProgressMediaView) actualView).setProgressEnabled(false);
            }
        }
    }

    /**
     * Displays the given list of comments combined with the voting for those comments.
     *
     * @param comments The list of comments to display.
     */
    private void displayComments(List<Comment> comments) {
        this.comments = ImmutableList.copyOf(comments);

        // show now
        commentsAdapter.set(comments, emptyMap(), feedItem.getUser());

        if (autoScrollTo.isPresent()) {
            scrollToComment(autoScrollTo.get());
            autoScrollTo = Optional.absent();
        }

        // load the votes for the comments and update, when we found any
        voteService.getCommentVotes(comments)
                .filter(votes -> !votes.isEmpty())
                .onErrorResumeNext(empty())
                .compose(bindToLifecycle())
                .subscribe(votes -> commentsAdapter.set(comments, votes, feedItem.getUser()));
    }

    /**
     * Returns the feed item that is displayed in this {@link PostFragment}.
     */
    public FeedItem getFeedItem() {
        return feedItem;
    }

    /**
     * Called from the {@link PostPagerFragment} if this fragment
     * is currently the active/selected fragment - or if it is not the active fragment anymore.
     *
     * @param active The new active status.
     */
    public void setActive(boolean active) {
        activeStateSubject.onNext(active);
    }

    @Override
    public void onAddNewTags(List<String> tags) {
        voteService.tag(feedItem, tags)
                .compose(bindToLifecycle())
                .lift(busyDialog(this))
                .subscribe(this::displayTags, defaultOnError());
    }

    /**
     * Creates a new instance of a {@link PostFragment} displaying the
     * given {@link FeedItem}.
     */
    public static PostFragment newInstance(FeedItem item) {
        checkNotNull(item, "Item must not be null");

        Bundle arguments = new Bundle();
        arguments.putParcelable(ARG_FEED_ITEM, item);

        PostFragment fragment = new PostFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    @SuppressWarnings("CodeBlock2Expr")
    @Override
    public boolean onCommentVoteClicked(Comment comment, Vote vote) {
        return doIfAuthorizedHelper.run(() -> {
            voteService.vote(comment, vote)
                    .compose(bindToLifecycle())
                    .subscribe(Actions.empty(), defaultOnError());
        });
    }

    @SuppressWarnings("CodeBlock2Expr")
    @Override
    public void onAnswerClicked(Comment comment) {
        Runnable retry = () -> onAnswerClicked(comment);

        doIfAuthorizedHelper.run(() -> {
            startActivityForResult(
                    WriteMessageActivity.answerToComment(getActivity(), feedItem, comment),
                    RequestCodes.WRITE_COMMENT);

        }, retry);
    }

    @Override
    public void onCommentAuthorClicked(Comment comment) {
        onUserClicked(comment.getName());
    }

    @Override
    public void onCommentMarkAsFavoriteClicked(Comment comment, boolean markAsFavorite) {
        Observable<Void> result;
        if (markAsFavorite) {
            result = favedCommentService.save(ImmutableFavedComment.builder()
                    .id(comment.getId())
                    .name(comment.getName())
                    .content(comment.getContent())
                    .created(comment.getCreated())
                    .up(comment.getUp())
                    .down(comment.getDown())
                    .mark(comment.getMark())
                    .thumb(feedItem.getThumb())
                    .itemId(feedItem.getId())
                    .flags(feedItem.getFlags())
                    .build());
        } else {
            result = favedCommentService.delete(comment.getId());
        }

        result.compose(bindUntilEvent(FragmentEvent.DESTROY_VIEW))
                .subscribe(Actions.empty(), defaultOnError());

        if (singleShotService.isFirstTime("kfav-userscript-hint")) {
            DialogBuilder.start(getContext())
                    .content(R.string.hint_kfav_userscript)
                    .positive(R.string.open_website, di -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://goo.gl/py7xNW"));
                        getContext().startActivity(intent);
                    })
                    .negative(R.string.ignore)
                    .show();
        }
    }

    @Override
    public void onTagClicked(Tag tag) {
        if (getParentFragment() instanceof PostPagerFragment)
            ((PostPagerFragment) getParentFragment()).onTagClicked(tag);
    }

    @Override
    public void onUserClicked(String username) {
        if (getParentFragment() instanceof PostPagerFragment)
            ((PostPagerFragment) getParentFragment()).onUsernameClicked(username);
    }

    private void onNewComments(NewComment response) {
        autoScrollToComment(response.getCommentId());
        displayComments(response.getComments());

        Snackbar.make(content, R.string.comment_written_successful, Snackbar.LENGTH_LONG).show();
        hideSoftKeyboard();
    }

    @SuppressWarnings("ConstantConditions")
    private void hideSoftKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getActivity()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);

            imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
        } catch (Exception ignored) {
        }
    }

    @Nullable
    private PreviewInfo getPreviewInfoFromCache() {
        Uri previewUri = UriHelper.of(getActivity()).thumbnail(feedItem);
        return localCacheService.getSizeInfo(feedItem.getId())
                .transform(info -> new PreviewInfo(info.getId(), previewUri, info.getWidth(), info.getHeight()))
                .orNull();
    }

    public void mediaHorizontalOffset(int offset) {
        viewer.setTranslationX(offset);
    }

    private class PlaceholderView extends View {
        int fixedHeight = AndroidUtility.dp(getActivity(), 150);

        public PlaceholderView() {
            super(PostFragment.this.getContext());
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(width, fixedHeight);
        }

        @Override
        public boolean onTouchEvent(@NonNull MotionEvent event) {
            return viewer.onTouchEvent(event);
        }
    }

    private class ScrollHandler extends RecyclerView.OnScrollListener {
        private final ToolbarActivity activity;

        public ScrollHandler(ToolbarActivity activity) {
            this.activity = activity;
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            if (isVideoFullScreen())
                return;

            // get our facts straight
            int recyclerHeight = recyclerView.getHeight();
            Optional<Integer> scrollEstimate = estimateRecyclerViewScrollY(recyclerView);
            boolean viewerVisible = scrollEstimate.isPresent();

            int scrollY = scrollEstimate.or(viewer.getHeight());
            int viewerHeight = viewer.getHeight();
            boolean doFancyScroll = viewerHeight < recyclerHeight;

            ScrollHideToolbarListener toolbar = activity.getScrollHideToolbarListener();
            if (!doFancyScroll || dy < 0 || scrollY > toolbar.getToolbarHeight()) {
                toolbar.onScrolled(dy);
            }

            float scroll = doFancyScroll ? 0.7f * scrollY : scrollY;

            if (doFancyScroll) {
                int clipTop = (int) (scroll + 0.5f);
                int clipBottom = viewer.getHeight() - (int) (scrollY - scroll + 0.5f);

                if (clipTop < clipBottom) {
                    viewer.setClipBoundsCompat(new Rect(0, clipTop, viewer.getRight(), clipBottom));
                } else {
                    viewerVisible = false;
                }
            } else {
                // reset bounds. we might have set some previously and want
                // to clear those bounds now.
                viewer.setClipBoundsCompat(null);
            }

            offsetMediaView(viewerVisible, scroll);

            // position the vote indicator
            float remaining = viewerHeight - scrollY;
            int tbVisibleHeight = toolbar.getVisibleHeight();
            float voteIndicatorY = Math.min(
                    (remaining - tbVisibleHeight) / 2,
                    (recyclerHeight - tbVisibleHeight) / 2) + tbVisibleHeight;

            voteAnimationIndicator.setTranslationY(voteIndicatorY);
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (!isVideoFullScreen() && newState == RecyclerView.SCROLL_STATE_IDLE) {
                int y = estimateRecyclerViewScrollY(recyclerView).or(Integer.MAX_VALUE);
                activity.getScrollHideToolbarListener().onScrollFinished(y);
            }
        }
    }

    /**
     * Positions the media view using the given offset (on the y axis)
     */
    private void offsetMediaView(boolean viewerVisible, float offset) {
        if (viewerVisible) {
            // finally position the viewer
            viewer.setTranslationY(-offset);
            viewer.setVisibility(View.VISIBLE);

            // position the repost badge, if it is visible
            if (repostHint.getVisibility() == View.VISIBLE) {
                repostHint.setTranslationY(viewer.getPaddingTop() - repostHint.getPivotY() - offset);
            }
        } else {
            viewer.setVisibility(View.INVISIBLE);
        }
    }

    private class FullscreenParams {
        private final float scale;
        private final float trY;
        private final float rotation;

        FullscreenParams() {
            int windowWidth = swipeRefreshLayout.getWidth();
            float windowHeight = swipeRefreshLayout.getHeight();

            //noinspection UnnecessaryLocalVariable
            int viewerWidth = windowWidth;
            int viewerHeight = viewer.getHeight() - viewer.getPaddingTop();

            viewer.setPivotY(viewer.getHeight() - 0.5f * viewerHeight);
            viewer.setPivotX(viewerWidth / 2.f);
            trY = (windowHeight / 2.f - viewer.getPivotY());

            float scaleRot = Math.min(
                    windowHeight / (float) viewerWidth,
                    windowWidth / (float) viewerHeight);

            float scaleNoRot = Math.min(
                    windowHeight / (float) viewerHeight,
                    windowWidth / (float) viewerWidth);

            // check if rotation is necessary
            if (scaleRot > scaleNoRot) {
                rotation = 90.f;
                scale = scaleRot;
            } else {
                rotation = 0.f;
                scale = scaleNoRot;
            }
        }
    }

    /**
     * Returns true, if the given tag looks like some "loop" tag.
     */
    private static boolean isLoopTag(String tag) {
        tag = tag.toLowerCase();
        return tag.contains("loop") && !(tag.contains("verschenkt") || tag.contains("verkackt"));
    }

    /**
     * Returns true, if the given url links to a static image.
     * This does only a check on the filename and not on the data.
     *
     * @param image The url of the image to check
     */
    private static boolean isStaticImage(FeedItem image) {
        return image.getImage().toLowerCase().matches(".*\\.(jpg|jpeg|png)");
    }

}
