package com.pr0gramm.app.ui.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentActivity;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
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
import android.widget.EditText;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.Pr0grammApplication;
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
import com.pr0gramm.app.services.LocalCacheService;
import com.pr0gramm.app.services.SeenService;
import com.pr0gramm.app.services.ShareHelper;
import com.pr0gramm.app.services.ShareProvider;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.services.UriHelper;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.services.VoteService;
import com.pr0gramm.app.ui.MainActivity;
import com.pr0gramm.app.ui.MergeRecyclerAdapter;
import com.pr0gramm.app.ui.OnOptionsItemSelected;
import com.pr0gramm.app.ui.OptionMenuHelper;
import com.pr0gramm.app.ui.PermissionHelperActivity;
import com.pr0gramm.app.ui.PreviewInfo;
import com.pr0gramm.app.ui.Screen;
import com.pr0gramm.app.ui.ScrollHideToolbarListener;
import com.pr0gramm.app.ui.SimpleTextWatcher;
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
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Actions;

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

/**
 * This fragment shows the content of one post.
 */
public class PostFragment extends BaseFragment implements
        NewTagDialogFragment.OnAddNewTagsListener,
        CommentsAdapter.CommentActionListener, InfoLineView.OnDetailClickedListener {

    private static final String ARG_FEED_ITEM = "PostFragment.post";

    private boolean active;
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

    @Bind(R.id.refresh)
    SwipeRefreshLayout swipeRefreshLayout;

    @Bind(R.id.player_container)
    ViewGroup playerContainer;

    @Bind(R.id.content)
    RecyclerView content;

    @Bind(R.id.vote_indicator)
    TextView voteAnimationIndicator;

    @Bind(R.id.repost_hint)
    View repostHint;

    private InfoLineView infoLineView;

    // start with an empty adapter here
    private MergeRecyclerAdapter adapter;
    private CommentsAdapter commentsAdapter;

    private Optional<Long> autoScrollTo = Optional.absent();
    private RecyclerView.OnScrollListener scrollHandler;

    private final LoginActivity.DoIfAuthorizedHelper doIfAuthorizedHelper = LoginActivity.helper(this);
    private PreviewInfo previewInfo;

    private List<Tag> tags;
    private List<Comment> comments;
    private boolean rewindOnLoad;

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

        if (!(getActivity() instanceof ToolbarActivity)) {
            throw new IllegalStateException("Fragment must be child of a ToolbarActivity.");
        }

        ToolbarActivity activity = (ToolbarActivity) getActivity();
        activity.getScrollHideToolbarListener().reset();

        // use height of the toolbar to configure swipe refresh layout.
        int abHeight = AndroidUtility.getActionBarContentOffset(getActivity());
        swipeRefreshLayout.setProgressViewOffset(false, 0, (int) (1.5 * abHeight));
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
        adapter.addAdapter(commentsAdapter);

        scrollHandler = new ScrollHandler(activity);
        content.addOnScrollListener(scrollHandler);

        // restore the postInfo, if possible.
        if (tags != null && comments != null) {
            displayTags(tags);
            displayComments(comments);
        }

        loadPostDetails();

        // show the repost badge if this is a repost
        if (isRepost()) {
            repostHint.setVisibility(View.VISIBLE);
            repostHint.setRotation(45);
        }
    }

    private boolean isRepost() {
        return localCacheService.isRepost(feedItem);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (scrollHandler != null) {
            content.removeOnScrollListener(scrollHandler);
            scrollHandler = null;
        }

        // restore orientation if the user closes this view
        Screen.unlockOrientation(getActivity());

        adapter = null;

        AndroidUtility.uninjectViews(this);
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
        CommentPostLine line = new CommentPostLine(getActivity());
        adapter.addAdapter(SingleViewAdapter.ofView(line));

        line.getCommentTextView().addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString().trim();
                line.getPostButton().setEnabled(text.length() > 0);
            }
        });

        line.getPostButton().setEnabled(false);
        line.getPostButton().setOnClickListener(view -> {
            Runnable action = () -> {
                EditText textView = line.getCommentTextView();
                String text = textView.getText().toString().trim();
                textView.setText("");

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

    /**
     * Returns true, if the given url links to a static image.
     * This does only a check on the filename and not on the data.
     *
     * @param image The url of the image to check
     */
    private boolean isStaticImage(FeedItem image) {
        return image.getImage().toLowerCase().matches(".*\\.(jpg|jpeg|png)");
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
                // hide the toolbar if required neccessary
                ((ToolbarActivity) activity).getScrollHideToolbarListener().hide();
            }

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

        // move to our target values.
        if (animated) {
            swipeRefreshLayout.setAlpha(0);
            ObjectAnimator.ofPropertyValuesHolder(swipeRefreshLayout,
                    ofFloat(View.ALPHA, 0, 1f))
                    .setDuration(500)
                    .start();

            ObjectAnimator.ofPropertyValuesHolder(viewer,
                    ofFloat(View.ROTATION, 0),
                    ofFloat(View.TRANSLATION_Y, 0),
                    ofFloat(View.SCALE_X, 1),
                    ofFloat(View.SCALE_Y, 1))
                    .setDuration(500)
                    .start();

            if (isRepost()) {
                repostHint.setVisibility(View.VISIBLE);
            }

        } else {
            viewer.setRotation(0.f);
            viewer.setTranslationY(0.f);
            viewer.setScaleX(1.f);
            viewer.setScaleY(1.f);
        }

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
        return swipeRefreshLayout.getVisibility() != View.VISIBLE;
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
        if (viewer != null)
            viewer.onStart();

        if (active) {
            onMarkedActive();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewer != null)
            viewer.onResume();

        // set ordering
        commentsAdapter.setPrioritizeOpComments(settings.prioritizeOpComments());
    }

    @Override
    public void onPause() {
        if (viewer != null)
            viewer.onPause();

        super.onPause();
    }

    @Override
    public void onStop() {
        if (viewer != null)
            viewer.onStop();

        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (viewer != null)
            viewer.onDestroy();

        super.onDestroy();

        // check that this fragment is removed!
        Pr0grammApplication.getRefWatcher().watch(this);

        // check that the viewer is removed too
        if (viewer != null) {
            Pr0grammApplication.getRefWatcher().watch(viewer);
            viewer = null;
        }
    }

    /**
     * Loads the information about the post. This includes the
     * tags and the comments.
     */
    private void loadPostDetails() {
        int delay = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) ? 500 : 100;

        feedService.loadPostDetails(feedItem.getId())
                .delay(delay, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .compose(bindUntilEvent(FragmentEvent.DESTROY))
                .subscribe(this::onPostReceived, defaultOnError());
    }

    @SuppressWarnings("CodeBlock2Expr")
    private void initializeInfoLine() {
        // get the vote from the service
        Observable<Vote> cachedVote = voteService.getVote(feedItem).compose(bindToLifecycle());

        infoLineView = new InfoLineView(getActivity());
        adapter.addAdapter(SingleViewAdapter.ofView(infoLineView));


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

        if (settings.animatePostOnVote()) {
            // quickly center the vote button
            scrollHandler.onScrolled(content, 0, 0);

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
                    voteAnimationIndicator.setVisibility(View.GONE);
                }
            });
        }
    }

    private void initializeMediaView() {
        int padding = AndroidUtility.getActionBarContentOffset(getActivity());

        MediaView.Binder binder = new MediaView.Binder(bindToLifecycle());

        // initialize a new viewer fragment
        MediaUri uri = MediaUri.of(UriHelper.get().media(feedItem));
        if (!uri.isLocal() && AndroidUtility.isOnMobile(getActivity())) {
            if (settings.confirmPlayOnMobile() == Settings.ConfirmOnMobile.ALL) {
                uri = uri.withDelay(true);
            } else if (settings.confirmPlayOnMobile() == Settings.ConfirmOnMobile.VIDEO
                    && uri.getMediaType() != MediaUri.MediaType.IMAGE) {

                uri = uri.withDelay(true);
            }
        }

        viewer = MediaViews.newInstance(getActivity(), binder, uri, () -> {
            //  mark this item seen. We do that in a background thread
            seenService.markAsSeen(feedItem);
        });

        registerTabListener(viewer);

        PreviewInfo previewInfo = this.previewInfo != null
                ? this.previewInfo : getPreviewInfoFromCache();

        if (previewInfo != null) {
            viewer.setPreviewImage(previewInfo, "TransitionTarget-" + feedItem.getId());
            viewer.postDelayed(this::onTransitionEnds, 350);
        } else {
            onTransitionEnds();
        }

        // add views in the correct order
        int idx = playerContainer.indexOfChild(voteAnimationIndicator);
        playerContainer.addView(viewer, idx);

        class PlaceholderView extends View {
            int fixedHeight = AndroidUtility.dp(getActivity(), 150);

            public PlaceholderView(Context context) {
                super(context);
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

        PlaceholderView placeholder = new PlaceholderView(getActivity());

        viewer.setPadding(0, padding, 0, 0);
        viewer.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int newHeight = viewer.getMeasuredHeight();
            if (newHeight != placeholder.fixedHeight) {
                placeholder.fixedHeight = newHeight;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    placeholder.requestLayout();
                } else {
                    // it looks like a requestLayout is not honored on pre kitkat devices
                    // if already in a layout pass.
                    placeholder.post(placeholder::requestLayout);
                }

                if (isVideoFullScreen()) {
                    realignFullScreen();
                }
            }
        });

        adapter.addAdapter(SingleViewAdapter.ofView(placeholder));
    }

    private void onTransitionEnds() {
        if (viewer != null && scrollHandler != null && content != null) {
            viewer.onTransitionEnds();
            scrollHandler.onScrolled(content, 0, 0);
        }
    }

    /**
     * Registers a tap listener on the given viewer instance. The listener is used
     * to handle double-tap-to-vote events from the view.
     *
     * @param viewer The viewer to register the tap listener to.
     */
    private void registerTabListener(MediaView viewer) {
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

    private static boolean isLoopTag(String tag) {
        tag = tag.toLowerCase();
        return tag.contains("loop") && !(tag.contains("verschenkt") || tag.contains("verkackt"));
    }

    /**
     * Displays the given list of comments combined with the votings for those comments.
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
                .onErrorResumeNext(Observable.empty())
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
        this.active = active;

        if (viewer == null)
            return;

        if (active) {
            onMarkedActive();
        } else {
            onMarkedInactive();
        }
    }

    /**
     * Called if this fragment becomes the active post fragment.
     */
    protected void onMarkedActive() {
        if (viewer != null) {
            viewer.playMedia();
        }
    }

    /**
     * Called if this fragment is not the active post fragment anymore.
     */
    protected void onMarkedInactive() {
        if (viewer != null) {
            viewer.stopMedia();
        }

        exitFullscreenAnimated(false);
    }

    /**
     */
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
    public void onTagClicked(Tag tag) {
        if (getParentFragment() instanceof PostPagerFragment)
            ((PostPagerFragment) getParentFragment()).onTagClicked(tag);
    }

    @Override
    public void onUserClicked(String username) {
        if (getParentFragment() instanceof PostPagerFragment)
            ((PostPagerFragment) getParentFragment()).onUsernameClicked(username);
    }

    public void onNewComments(NewComment response) {
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
    public PreviewInfo getPreviewInfoFromCache() {
        Uri previewUri = UriHelper.of(getActivity()).thumbnail(feedItem);
        return localCacheService.getSizeInfo(feedItem.getId())
                .transform(info -> new PreviewInfo(info.getId(), previewUri, info.getWidth(), info.getHeight()))
                .orNull();
    }

    public void mediaHorizontalOffset(int offset) {
        viewer.setTranslationX(offset);
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
            int scrollY = estimateRecyclerViewScrollY(recyclerView).or(recyclerHeight);

            int viewerHeight = viewer.getHeight();
            boolean doFancyScroll = viewerHeight < recyclerHeight;

            ScrollHideToolbarListener toolbar = activity.getScrollHideToolbarListener();
            if (!doFancyScroll || dy < 0 || scrollY > 1f * toolbar.getToolbarHeight()) {
                toolbar.onScrolled(dy);
            }

            int halfScrollOffset = 0; // toolbar.getToolbarHeight();
            float scroll = scrollY < halfScrollOffset || !doFancyScroll
                    ? scrollY
                    : halfScrollOffset + 0.7f * (scrollY - halfScrollOffset);

            offsetMediaView(scroll);

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
    private void offsetMediaView(float offset) {
        ViewGroup.MarginLayoutParams layout = (ViewGroup.MarginLayoutParams) viewer.getLayoutParams();

        // finally position the viewer
        viewer.setTranslationY(-offset);

        // position the repost badge, if it is visible
        if (repostHint.getVisibility() == View.VISIBLE) {
            repostHint.setTranslationY(viewer.getPaddingTop() - repostHint.getPivotY() - offset);
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

            // check if rotation is neccessary
            if (scaleRot > scaleNoRot) {
                rotation = 90.f;
                scale = scaleRot;
            } else {
                rotation = 0.f;
                scale = scaleNoRot;
            }
        }
    }
}
