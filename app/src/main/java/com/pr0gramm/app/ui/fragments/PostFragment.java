package com.pr0gramm.app.ui.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.pr0gramm.app.AndroidUtility;
import com.pr0gramm.app.MergeRecyclerAdapter;
import com.pr0gramm.app.OptionMenuHelper;
import com.pr0gramm.app.OptionMenuHelper.OnOptionsItemSelected;
import com.pr0gramm.app.Pr0grammApplication;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.ShareProvider;
import com.pr0gramm.app.Uris;
import com.pr0gramm.app.api.pr0gramm.response.NewComment;
import com.pr0gramm.app.api.pr0gramm.response.Post;
import com.pr0gramm.app.api.pr0gramm.response.Tag;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedService;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.feed.Vote;
import com.pr0gramm.app.services.LocalCacheService;
import com.pr0gramm.app.services.ProxyService;
import com.pr0gramm.app.services.SeenService;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.services.VoteService;
import com.pr0gramm.app.ui.ScrollHideToolbarListener;
import com.pr0gramm.app.ui.SimpleTextWatcher;
import com.pr0gramm.app.ui.SingleViewAdapter;
import com.pr0gramm.app.ui.ZoomViewActivity;
import com.pr0gramm.app.ui.dialogs.LoginActivity;
import com.pr0gramm.app.ui.dialogs.NewTagDialogFragment;
import com.pr0gramm.app.ui.dialogs.ReplyCommentDialogFragment;
import com.pr0gramm.app.ui.views.CommentPostLine;
import com.pr0gramm.app.ui.views.CommentsAdapter;
import com.pr0gramm.app.ui.views.InfoLineView;
import com.pr0gramm.app.ui.views.viewer.MediaUri;
import com.pr0gramm.app.ui.views.viewer.MediaView;
import com.pr0gramm.app.ui.views.viewer.MediaViews;
import com.squareup.picasso.Picasso;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;
import rx.Observable;
import rx.Subscription;
import rx.functions.Actions;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.pr0gramm.app.AndroidUtility.ifNotNull;
import static com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity;
import static com.pr0gramm.app.ui.ScrollHideToolbarListener.estimateRecyclerViewScrollY;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.showErrorString;
import static com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog;
import static rx.android.app.AppObservable.bindFragment;

/**
 * This fragment shows the content of one post.
 */
public class PostFragment extends RoboFragment implements
        NewTagDialogFragment.OnAddNewTagsListener,
        ReplyCommentDialogFragment.OnNewCommentsListener,
        CommentsAdapter.CommentActionListener, InfoLineView.OnDetailClickedListener {

    private static final Logger logger = LoggerFactory.getLogger(PostFragment.class);

    private static final String ARG_FEED_ITEM = "PostFragment.post";

    private boolean active;
    private FeedItem feedItem;
    private MediaView viewer;

    @Inject
    private FeedService feedService;

    @Inject
    private VoteService voteService;

    @Inject
    private Settings settings;

    @Inject
    private SeenService seenService;

    @Inject
    private DownloadManager downloadManager;

    @Inject
    private SingleShotService singleShotService;

    @Inject
    private LocalCacheService localCacheService;

    @Inject
    private ProxyService proxyService;

    @Inject
    private UserService userService;

    @Inject
    private Picasso picasso;

    @InjectView(R.id.refresh)
    private SwipeRefreshLayout swipeRefreshLayout;

    @InjectView(R.id.player_container)
    private ViewGroup playerContainer;

    @InjectView(R.id.content)
    private RecyclerView content;

    @InjectView(R.id.vote_indicator)
    private TextView voteAnimationIndicator;

    private InfoLineView infoLineView;

    // start with an empty adapter here
    private MergeRecyclerAdapter adapter;
    private CommentsAdapter commentsAdapter;

    private Optional<Long> autoScrollTo = Optional.absent();
    private RecyclerView.OnScrollListener scrollHandler;

    private final LoginActivity.DoIfAuthorizedHelper doIfAuthorizedHelper = LoginActivity.helper(this);
    private PreviewInfo previewInfo;

    private Subscription rxDetails;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        // get the item that is to be displayed.
        feedItem = getArguments().getParcelable(ARG_FEED_ITEM);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_post, container, false);
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
        swipeRefreshLayout.setOnRefreshListener(this::loadPostDetails);

        swipeRefreshLayout.setKeepScreenOn(settings.keepScreenOn());

        adapter = new MergeRecyclerAdapter();
        content.setItemAnimator(null);
        content.setLayoutManager(new LinearLayoutManager(getActivity()));
        content.setAdapter(adapter);

        initializeMediaView();
        initializeInfoLine();
        initializeCommentPostLine();

        commentsAdapter = new CommentsAdapter();
        commentsAdapter.setCommentActionListener(this);
        adapter.addAdapter(commentsAdapter);

        scrollHandler = new ScrollHandler(activity);
        content.addOnScrollListener(scrollHandler);

        loadPostDetails();

        // show an empty list of tags first - this displays the 'Add' button.
        displayTags(Collections.<Tag>emptyList());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (scrollHandler != null) {
            content.removeOnScrollListener(scrollHandler);
            scrollHandler = null;
        }

        adapter = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        doIfAuthorizedHelper.onActivityResult(requestCode, resultCode);
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
        bindFragment(this, voteService.postComment(feedItem, 0, text))
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
        boolean isImage = isStaticImage(feedItem.getImage());

        ifNotNull(menu.findItem(R.id.action_refresh),
                item -> item.setVisible(settings.showRefreshButton()));

        ifNotNull(menu.findItem(R.id.action_zoom),
                item -> item.setVisible(isImage));

        ifNotNull(menu.findItem(R.id.action_share_image),
                item -> item.setVisible(ShareProvider.canShare(feedItem)));

        ifNotNull(menu.findItem(R.id.action_search_image),
                item -> item.setVisible(isImage && settings.showGoogleImageButton()));
    }

    /**
     * Returns true, if the given url links to a static image.
     * This does only a check on the filename and not on the data.
     *
     * @param image The url of the image to check
     */
    private boolean isStaticImage(String image) {
        return image.toLowerCase().matches(".*\\.(jpg|jpeg|png)");
    }

    @OnOptionsItemSelected(R.id.action_zoom)
    public void openImageInFullscreen() {
        boolean hq = settings.loadHqInZoomView();
        Intent intent = ZoomViewActivity.newIntent(getActivity(), feedItem, hq);
        startActivity(intent);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return OptionMenuHelper.dispatch(this, item) || super.onOptionsItemSelected(item);
    }

    @OnOptionsItemSelected(R.id.action_search_image)
    public void searchImage() {
        Uri uri = Uri.parse("https://www.google.com/searchbyimage").buildUpon()
                .appendQueryParameter("hl", "en")
                .appendQueryParameter("safe", "off")
                .appendQueryParameter("site", "search")
                .appendQueryParameter("image_url", Uris.get().media(feedItem).toString().replace("https://", "http://"))
                .build();

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
    }

    @OnOptionsItemSelected(R.id.action_share_post)
    public void sharePost() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        if (feedItem.getPromotedId() > 0) {
            intent.putExtra(Intent.EXTRA_TEXT,
                    Uris.get().post(FeedType.PROMOTED, feedItem.getId()).toString());
        } else {
            intent.putExtra(Intent.EXTRA_TEXT,
                    Uris.get().post(FeedType.NEW, feedItem.getId()).toString());
        }
        startActivity(intent);
    }

    @OnOptionsItemSelected(R.id.action_share_direct_link)
    public void shareDirectLink() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, Uris.get().media(feedItem).toString());
        startActivity(intent);
    }

    @OnOptionsItemSelected(R.id.action_share_image)
    public void shareImage() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_STREAM, ShareProvider.getShareUri(getActivity(), feedItem));
        startActivity(intent);
    }

    @OnOptionsItemSelected(R.id.action_refresh)
    public void refreshWithIndicator() {
        if (swipeRefreshLayout.isRefreshing())
            return;

        swipeRefreshLayout.setRefreshing(true);
        swipeRefreshLayout.postDelayed(this::loadPostDetails, 500);
    }

    @OnOptionsItemSelected(R.id.action_download)
    public void downloadPostMedia() {
        // download over proxy to use caching
        Uri url = proxyService.proxy(Uris.get().media(feedItem, true));

        File external;
        if (settings.downloadLocation().equals(getString(R.string.pref_downloadLocation_value_downloads))) {
            external = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        } else if (settings.downloadLocation().equals(getString(R.string.pref_downloadLocation_value_pictures))) {
            external = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

        } else {
            external = Environment.getExternalStorageDirectory();
        }

        File targetDirectory = new File(external, "pr0gramm");
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            showErrorString(getChildFragmentManager(), getString(R.string.error_could_not_create_download_directory));
            return;
        }

        DateTimeFormatter format = DateTimeFormat.forPattern("yyyyMMdd-HHmmss");
        String fileType = feedItem.getImage().toLowerCase().replaceFirst("^.*\\.([a-z]+)$", "$1");
        String prefix = Joiner.on("-").join(
                feedItem.getCreated().toString(format),
                feedItem.getUser(),
                "id" + feedItem.getId());

        String name = prefix.replaceAll("[^A-Za-z0-9_-]+", "") + "." + fileType;

        DownloadManager.Request request = new DownloadManager.Request(url);
        request.setVisibleInDownloadsUi(false);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setTitle(name);
        request.setDestinationUri(Uri.fromFile(new File(targetDirectory, name)));

        request.allowScanningByMediaScanner();

        downloadManager.enqueue(request);
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

        if (rxDetails != null)
            rxDetails.unsubscribe();

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

        Observable<Post> details = feedService.loadPostDetails(feedItem.getId());
        rxDetails = bindFragment(this, details.delay(delay, TimeUnit.MILLISECONDS))
                .subscribe(this::onPostReceived, defaultOnError());
    }

    @SuppressWarnings("CodeBlock2Expr")
    private void initializeInfoLine() {
        // get the vote from the service
        Observable<Vote> cachedVote = voteService.getVote(feedItem);

        infoLineView = new InfoLineView(getActivity());
        adapter.addAdapter(SingleViewAdapter.ofView(infoLineView));


        boolean isSelfPost = userService.getName()
                .transform(name -> name.equalsIgnoreCase(feedItem.getUser()))
                .or(false);

        // display the feed item in the view
        infoLineView.setFeedItem(feedItem, isSelfPost, bindFragment(this, cachedVote));

        infoLineView.setOnDetailClickedListener(this);

        // register the vote listener
        infoLineView.setOnVoteListener(vote -> {
            Runnable action = () -> {
                showPostVoteAnimation(vote);

                bindFragment(this, voteService.vote(feedItem, vote))
                        .subscribe(Actions.empty(), defaultOnError());
            };

            Runnable retry = () -> infoLineView.getVoteView().setVote(vote);
            return doIfAuthorizedHelper.run(action, retry);
        });

        // and a vote listener vor voting tags.
        infoLineView.setTagVoteListener((tag, vote) -> {
            Runnable action = () -> {
                bindFragment(this, voteService.vote(tag, vote))
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
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0, 0.6f, 0.7f, 0.6f, 0),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 0.7f, 1.3f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.7f, 1.3f));

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

        //noinspection Convert2Lambda
        MediaView.Binder binder = new MediaView.Binder() {
            @Override
            public <T> Observable<T> bind(Observable<T> observable) {
                return bindFragment(PostFragment.this, observable);
            }
        };

        // initialize a new viewer fragment
        MediaUri uri = MediaUri.of(Uris.get().media(feedItem));

        // delay playback on mobile
        if (settings.confirmPlayOnMobile() && AndroidUtility.isOnMobile(getActivity())) {
            if (uri.getMediaType() != MediaUri.MediaType.IMAGE) {
                uri = uri.withDelay(true);
            }
        }

        viewer = MediaViews.newInstance(getActivity(), binder, uri, () -> {
            //  mark this item seen. We do that in a background thread
            AsyncTask.execute(() -> seenService.markAsSeen(feedItem));
        });

        registerDoubleTapListener(viewer);

        PreviewInfo previewInfo = this.previewInfo != null
                ? this.previewInfo : getPreviewInfoFromCache();

        if (previewInfo != null) {
            viewer.setPreviewImage(previewInfo, "TransitionTarget-" + feedItem.getId());
            viewer.postDelayed(viewer::onTransitionEnds, 500);
        } else {
            viewer.onTransitionEnds();
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
            }
        });

        adapter.addAdapter(SingleViewAdapter.ofView(placeholder));
    }

    /**
     * Registers a tap listener on the given viewer instance. The listener is used
     * to handle double-tap-to-vote events from the view.
     *
     * @param viewer The viewer to register the tap listener to.
     */
    private void registerDoubleTapListener(MediaView viewer) {
        if (settings.doubleTapToUpvote()) {
            viewer.setTapListener(new MediaView.TapListenerAdapter() {
                @Override
                public boolean onDoubleTap() {
                    infoLineView.getVoteView().triggerUpVoteClicked();
                    return true;
                }
            });
        }
    }

    /**
     * Called with the downloaded post information.
     *
     * @param post The post information that was downloaded.
     */
    private void onPostReceived(Post post) {
        swipeRefreshLayout.setRefreshing(false);

        // update tags from post
        displayTags(post.getTags());
        displayComments(post.getComments());
    }

    private void displayTags(List<Tag> tags_) {
        List<Tag> tags = localCacheService.enhanceTags(feedItem.getId(), tags_);
        bindFragment(this, voteService.getTagVotes(tags)).subscribe(votes_ -> {
            Map<Tag, Vote> votes = Maps.toMap(tags,
                    tag -> firstNonNull(votes_.get((long) tag.getId()), Vote.NEUTRAL));

            infoLineView.setTags(votes);
        }, Actions.empty());
    }

    /**
     * Displays the given list of comments combined with the votings for those comments.
     *
     * @param comments The list of comments to display.
     */
    private void displayComments(List<Post.Comment> comments) {
        bindFragment(this, voteService.getCommentVotes(comments)).subscribe(votes -> {
            commentsAdapter.setOp(Optional.of(feedItem.getUser()));
            commentsAdapter.setComments(comments, votes);

            if (autoScrollTo.isPresent()) {
                scrollToComment(autoScrollTo.get());
                autoScrollTo = Optional.absent();
            }
        }, defaultOnError());
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
    }

    /**
     */
    @Override
    public void onAddNewTags(List<String> tags) {
        bindFragment(this, voteService.tag(feedItem, tags))
                .lift(busyDialog(this))
                .subscribe(this::displayTags, defaultOnError());
    }

    /**
     * Creates a new instance of a {@link PostFragment} displaying the
     * given {@link com.pr0gramm.app.feed.FeedItem}.
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
    public boolean onCommentVoteClicked(Post.Comment comment, Vote vote) {
        return doIfAuthorizedHelper.run(() -> {
            bindFragment(this, voteService.vote(comment, vote))
                    .subscribe(Actions.empty(), defaultOnError());
        });
    }

    @SuppressWarnings("CodeBlock2Expr")
    @Override
    public void onAnswerClicked(Post.Comment comment) {
        Runnable retry = () -> onAnswerClicked(comment);

        doIfAuthorizedHelper.run(() -> {
            ReplyCommentDialogFragment
                    .newInstance(feedItem.getId(), Optional.fromNullable(comment))
                    .show(getChildFragmentManager(), null);

        }, retry);
    }

    @Override
    public void onCommentAuthorClicked(Post.Comment comment) {
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

    @Override
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
        Uri previewUri = Uris.of(settings).thumbnail(feedItem);
        return localCacheService.getSizeInfo(feedItem.getId())
                .transform(info -> new PreviewInfo(info.getId(), previewUri, info.getWidth(), info.getHeight()))
                .orNull();
    }

    private class ScrollHandler extends RecyclerView.OnScrollListener {
        private final ToolbarActivity activity;

        public ScrollHandler(ToolbarActivity activity) {
            this.activity = activity;
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            // get our facts straight
            int recyclerHeight = recyclerView.getHeight();
            int scrollY = estimateRecyclerViewScrollY(recyclerView).or(-recyclerHeight);

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
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                int y = estimateRecyclerViewScrollY(recyclerView).or(Integer.MIN_VALUE);
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
        if(viewer.isTransformable()) {
            viewer.setTranslationY(-offset);

            if(layout.topMargin != 0) {
                layout.topMargin = 0;
                viewer.requestLayout();
            }
        } else {
            viewer.setTranslationY(0);

            layout.topMargin = -(int)offset;
            viewer.requestLayout();
        }
    }

    public static final class PreviewInfo {
        private final long itemId;
        private final int width;
        private final int height;
        private Drawable preview;
        private Uri previewUri;


        public PreviewInfo(long itemId, int width, int height) {
            this.itemId = itemId;
            this.width = width;
            this.height = height;
        }

        public PreviewInfo(long itemId, Drawable preview, int width, int height) {
            this(itemId, width, height);
            this.preview = preview;
        }

        public PreviewInfo(long itemId, Uri previewUri, int width, int height) {
            this(itemId, width, height);
            this.previewUri = previewUri;
        }

        public long getItemId() {
            return itemId;
        }

        public Drawable getPreview() {
            return preview;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public Uri getPreviewUri() {
            return previewUri;
        }
    }
}
