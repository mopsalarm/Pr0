package com.pr0gramm.app.ui.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.DownloadManager;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.pr0gramm.app.AndroidUtility;
import com.pr0gramm.app.MergeRecyclerAdapter;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.api.pr0gramm.response.Post;
import com.pr0gramm.app.api.pr0gramm.response.Tag;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedService;
import com.pr0gramm.app.feed.Vote;
import com.pr0gramm.app.services.LocalCacheService;
import com.pr0gramm.app.services.ProxyService;
import com.pr0gramm.app.services.SeenService;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.services.VoteService;
import com.pr0gramm.app.ui.SimpleTextWatcher;
import com.pr0gramm.app.ui.ZoomViewActivity;
import com.pr0gramm.app.ui.dialogs.NewCommentDialogFragment;
import com.pr0gramm.app.ui.dialogs.NewTagDialogFragment;
import com.pr0gramm.app.ui.views.CommentPostLine;
import com.pr0gramm.app.ui.views.CommentsAdapter;
import com.pr0gramm.app.ui.views.InfoLineView;
import com.pr0gramm.app.ui.views.viewer.MediaView;
import com.pr0gramm.app.ui.views.viewer.MediaViews;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;
import rx.Observable;
import rx.functions.Actions;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity;
import static com.pr0gramm.app.ui.ScrollHideToolbarListener.estimateRecyclerViewScrollY;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.showErrorString;
import static com.pr0gramm.app.ui.dialogs.LoginDialogFragment.doIfAuthorized;
import static com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog;
import static rx.android.observables.AndroidObservable.bindFragment;

/**
 * This fragment shows the content of one post.
 */
public class PostFragment extends RoboFragment implements
        NewTagDialogFragment.OnAddNewTagsListener,
        NewCommentDialogFragment.OnNewCommentsListener,
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

    @InjectView(R.id.refresh)
    private SwipeRefreshLayout swipeRefreshLayout;

    @InjectView(R.id.content)
    private RecyclerView content;

    private InfoLineView infoLineView;

    // start with an empty adapter here
    private MergeRecyclerAdapter<RecyclerView.Adapter> adapter;
    private CommentsAdapter commentsAdapter;
    private TextView voteAnimationIndicator;

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

        if (getActivity() instanceof ToolbarActivity) {
            ToolbarActivity activity = (ToolbarActivity) getActivity();
            activity.getScrollHideToolbarListener().reset();

            content.addOnScrollListener(onScrollListener);
        }

        // use height of the toolbar to configure swipe refresh layout.
        int abHeight = AndroidUtility.getActionBarSize(getActivity());
        swipeRefreshLayout.setProgressViewOffset(false, 0, (int) (1.5 * abHeight));
        swipeRefreshLayout.setColorSchemeResources(R.color.primary);
        swipeRefreshLayout.setOnRefreshListener(this::loadPostDetails);

        swipeRefreshLayout.setKeepScreenOn(settings.keepScreenOn());

        adapter = new MergeRecyclerAdapter<>();
        content.setAdapter(adapter);
        content.setLayoutManager(new LinearLayoutManager(getActivity()));

        initializeMediaView();
        initializeInfoLine();
        initializeCommentPostLine();

        commentsAdapter = new CommentsAdapter();
        commentsAdapter.setCommentActionListener(this);
        adapter.addAdapter(commentsAdapter);

        loadPostDetails();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        content.removeOnScrollListener(onScrollListener);
    }

    private void initializeCommentPostLine() {
        CommentPostLine line = new CommentPostLine(getActivity());
        adapter.addView(line);

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
            doIfAuthorized(this, action, action);
        });
    }

    private void writeComment(String text) {
        bindFragment(this, voteService.postComment(feedItem, 0, text))
                .lift(busyDialog(this))
                .subscribe(this::displayComments, defaultOnError());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_post, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem refresh = menu.findItem(R.id.action_refresh);
        if (refresh != null) {
            refresh.setVisible(settings.showRefreshButton());
        }

        MenuItem item = menu.findItem(R.id.action_zoom);
        if (item != null) {
            item.setVisible(isStaticImage(feedItem.getImage()));
        }

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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.action_refresh) {
            doRefreshWithIndicator();
            return true;
        }

        if (item.getItemId() == R.id.action_download) {
            logger.info("Request download of post #" + feedItem.getId());
            downloadPostMedia();
            return true;
        }

        if (item.getItemId() == R.id.action_zoom) {
            boolean hq = settings.loadHqInZoomView();
            Intent intent = ZoomViewActivity.newIntent(getActivity(), feedItem, hq);
            startActivity(intent);
        }

        if(item.getItemId() == R.id.action_share) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, MediaViews.url(feedItem));
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    private void doRefreshWithIndicator() {
        if (swipeRefreshLayout.isRefreshing())
            return;

        swipeRefreshLayout.setRefreshing(true);
        swipeRefreshLayout.postDelayed(this::loadPostDetails, 500);
    }

    private void downloadPostMedia() {
        // download over proxy to use caching
        Uri url = Uri.parse(proxyService.proxy(MediaViews.url(feedItem)));
        File external;
        if (settings.downloadLocation().equals("downloads")) {
            external = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        }else if(settings.downloadLocation().equals("root")){
            external = Environment.getExternalStorageDirectory();
        }else{
            external = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
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

        super.onDestroy();
    }

    /**
     * Loads the information about the post. This includes the
     * tags and the comments.
     */
    private void loadPostDetails() {
        Observable<Post> details = feedService.loadPostDetails(feedItem.getId());
        bindFragment(this, details).subscribe(this::onPostReceived, defaultOnError());
    }

    @SuppressWarnings("CodeBlock2Expr")
    private void initializeInfoLine() {
        // get the vote from the service
        Observable<Vote> cachedVote = voteService.getVote(feedItem);

        infoLineView = new InfoLineView(getActivity());
        adapter.addView(infoLineView);

        // display the feed item in the view
        infoLineView.setFeedItem(feedItem, bindFragment(this, cachedVote));

        infoLineView.setOnDetailClickedListener(this);

        // register the vote listener
        infoLineView.setOnVoteListener(vote -> {
            Runnable action = () -> {
                showPostVoteAnimation(vote);

                bindFragment(this, voteService.vote(feedItem, vote))
                        .subscribe(Actions.empty(), defaultOnError());
            };

            Runnable retry = () -> infoLineView.getVoteView().setVote(vote);
            return doIfAuthorized(PostFragment.this, action, retry);
        });

        // and a vote listener vor voting tags.
        infoLineView.setTagVoteListener((tag, vote) -> {
            Runnable action = () -> {
                bindFragment(this, voteService.vote(tag, vote))
                        .subscribe(Actions.empty(), defaultOnError());
            };

            return doIfAuthorized(PostFragment.this, action, action);
        });

        infoLineView.getAddTagView().setOnClickListener(v -> {
            NewTagDialogFragment dialog = new NewTagDialogFragment();
            dialog.show(getChildFragmentManager(), null);
        });
    }

    private void showPostVoteAnimation(Vote vote) {
        if (vote == null || vote == Vote.NEUTRAL)
            return;

        if (settings.animatePostOnVote()) {
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
        LayoutInflater inflater = LayoutInflater.from(getActivity());

        //noinspection Convert2Lambda
        MediaView.Binder binder = new MediaView.Binder() {
            @Override
            public <T> Observable<T> bind(Observable<T> observable) {
                return bindFragment(PostFragment.this, observable);
            }
        };

        // initialize a new viewer fragment
        String url = MediaViews.url(feedItem);

        // delay playback on mobile
        if (settings.confirmPlayOnMobile() && AndroidUtility.isOnMobile(getActivity()))
            url = MediaViews.delay(url);

        viewer = MediaViews.newInstance(getActivity(), binder, url);
        viewer.setOnDoubleTapListener(this::onMediaViewDoubleTapped);

        // this provides an animation while voting
        voteAnimationIndicator = (TextView) inflater.inflate(R.layout.viewer_vote_indicators, null);

        // wrap into a container before adding
        FrameLayout viewerContainer = (FrameLayout) inflater
                .inflate(R.layout.post_player_container, new FrameLayout(getActivity()), false);

        viewerContainer.addView(viewer);
        viewerContainer.addView(voteAnimationIndicator);
        adapter.addView(viewerContainer);
    }

    private void onMediaViewDoubleTapped() {
        if (settings.doubleTapToUpvote()) {
            infoLineView.getVoteView().triggerUpVoteClicked();
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
        //  mark this item seen. We do that in a background thread
        AsyncTask.execute(() -> seenService.markAsSeen(feedItem));
        viewer.playMedia();
    }

    /**
     * Called if this fragment is not the active post fragment anymore.
     */
    protected void onMarkedInactive() {
        viewer.stopMedia();
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
        return doIfAuthorized(this, () -> {
            bindFragment(this, voteService.vote(comment, vote))
                    .subscribe(Actions.empty(), defaultOnError());
        });
    }

    @SuppressWarnings("CodeBlock2Expr")
    @Override
    public void onAnswerClicked(Post.Comment comment) {
        Runnable retry = () -> onAnswerClicked(comment);

        doIfAuthorized(this, () -> {
            NewCommentDialogFragment
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
    public void onNewComments(List<Post.Comment> comments) {
        displayComments(comments);
    }

    private final RecyclerView.OnScrollListener onScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            ToolbarActivity activity = (ToolbarActivity) getActivity();
            activity.getScrollHideToolbarListener().onScrolled(dy);
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                int y = estimateRecyclerViewScrollY(recyclerView);

                ToolbarActivity activity = (ToolbarActivity) getActivity();
                activity.getScrollHideToolbarListener().onScrollFinished(y);
            }
        }
    };
}
