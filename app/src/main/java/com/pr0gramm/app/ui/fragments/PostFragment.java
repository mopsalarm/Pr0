package com.pr0gramm.app.ui.fragments;

import android.app.DownloadManager;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Editable;
import android.util.Log;
import android.view.*;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.pr0gramm.app.AndroidUtility;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.api.pr0gramm.response.Post;
import com.pr0gramm.app.api.pr0gramm.response.Tag;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedService;
import com.pr0gramm.app.feed.Vote;
import com.pr0gramm.app.services.SeenService;
import com.pr0gramm.app.services.VoteService;
import com.pr0gramm.app.ui.SimpleTextWatcher;
import com.pr0gramm.app.ui.ZoomViewActivity;
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment;
import com.pr0gramm.app.ui.dialogs.NewCommentDialogFragment;
import com.pr0gramm.app.ui.dialogs.NewTagDialogFragment;
import com.pr0gramm.app.ui.views.CommentsAdapter;
import com.pr0gramm.app.ui.views.InfoLineView;
import com.pr0gramm.app.ui.views.VerticalScrollView;
import com.pr0gramm.app.ui.views.viewer.MediaView;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;
import rx.Observable;
import rx.functions.Actions;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity;
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
        NewCommentDialogFragment.OnAddNewCommentListener,
        CommentsAdapter.CommentActionListener, InfoLineView.OnDetailClickedListener {

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

    @InjectView(R.id.list)
    private LinearLayout list;

    @InjectView(R.id.refresh)
    private SwipeRefreshLayout swipeRefreshLayout;

    @InjectView(R.id.info_line)
    private InfoLineView infoLineView;

    @InjectView(R.id.scroll)
    private VerticalScrollView scrollView;

    @InjectView(R.id.player_container)
    private FrameLayout viewerContainer;

    @InjectView(R.id.comment_post)
    private View commentPostView;

    @InjectView(R.id.comment_text)
    private EditText commentTextView;

    // start with an empty adapter here
    private CommentsAdapter adapter = new CommentsAdapter();

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

            scrollView.setOnScrollListener((oldTop, top) -> {
                int dy = top - oldTop;
                activity.getScrollHideToolbarListener().onScrolled(dy);
            });
        }

        // use height of the toolbar to configure swipe refresh layout.
        int abHeight = AndroidUtility.getActionBarSize(getActivity());
        swipeRefreshLayout.setProgressViewOffset(false, 0, (int) (1.5 * abHeight));
        swipeRefreshLayout.setColorSchemeResources(R.color.primary);
        swipeRefreshLayout.setOnRefreshListener(this::loadPostDetails);

        // TODO Think of something nicer for the comments
        // commentsView.setLayoutManager(new WrapContentLinearLayoutManager(getActivity(),
        //         LinearLayoutManager.VERTICAL, false));

        initializeInfoLine();
        initializePlayerFragment();
        initializeCommentPostLine();
        loadPostDetails();
    }

    private void initializeCommentPostLine() {
        commentTextView.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String text = commentTextView.getText().toString().trim();
                commentPostView.setEnabled(text.length() > 0);
            }
        });

        commentPostView.setEnabled(false);
        commentPostView.setOnClickListener(view -> {
            Runnable action = () -> {
                String text = commentTextView.getText().toString().trim();
                commentTextView.setText("");

                onAddNewCommment(0, text);
            };
            doIfAuthorized(this, action, action);
        });
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_post, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_post_refresh).setVisible(settings.showRefreshButton());

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

        if (item.getItemId() == R.id.action_post_refresh) {
            doRefreshWithIndicator();
            return true;
        }

        if (item.getItemId() == R.id.action_download) {
            Log.i("Post", "Request download of post #" + feedItem.getId());
            downloadPostMedia();
            return true;
        }

        if (item.getItemId() == R.id.action_zoom) {
            Intent intent = ZoomViewActivity.newIntent(getActivity(), feedItem.getImage());
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    private void doRefreshWithIndicator() {
        swipeRefreshLayout.setRefreshing(true);
        swipeRefreshLayout.postDelayed(this::loadPostDetails, 1000);
    }

    private void downloadPostMedia() {
        Uri url = Uri.parse("http://img.pr0gramm.com/" + feedItem.getImage());

        File external = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
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

    private void initializeInfoLine() {
        // get the vote from the service
        Observable<Vote> cachedVote = voteService.getVote(feedItem);

        // display the feed item in the view
        infoLineView.setFeedItem(feedItem, bindFragment(this, cachedVote));

        infoLineView.setOnDetailClickedListener(this);

        // register the vote listener
        infoLineView.setOnVoteListener(vote -> {
            Runnable action = () -> {
                bindFragment(this, voteService.vote(feedItem, vote))
                        .subscribe(Actions.empty(), defaultOnError());
            };

            Runnable retry = () -> infoLineView.getVoteView().setVote(vote);
            return doIfAuthorized(PostFragment.this, action, retry);
        });

        infoLineView.getAddTagView().setOnClickListener(v -> {
            NewTagDialogFragment dialog = new NewTagDialogFragment();
            dialog.show(getChildFragmentManager(), null);
        });
    }

    private void initializePlayerFragment() {
        //noinspection Convert2Lambda
        MediaView.Binder binder = new MediaView.Binder() {
            @Override
            public <T> Observable<T> bind(Observable<T> observable) {
                return bindFragment(PostFragment.this, observable);
            }

            @Override
            public void onError(String text) {
                ErrorDialogFragment.showErrorString(getFragmentManager(), text);
            }
        };

        // initialize a new viewer fragment
        viewer = MediaView.newInstance(getActivity(), binder, feedItem);
        viewer.setOnDoubleTapListener(this::onMediaViewDoubleTapped);
        viewerContainer.addView(viewer);
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
        infoLineView.setTags(post.getTags());

        displayComments(post.getComments());
    }

    /**
     * Displays the given list of comments combined with the votings for those comments.
     *
     * @param comments The list of comments to display.
     */
    private void displayComments(List<Post.Comment> comments) {
        bindFragment(this, voteService.getCommentVotes(comments)).subscribe(votes -> {
            // and display the comments
            adapter = new CommentsAdapter();
            adapter.addVoteCache(votes);
            adapter.addComments(comments);

            // remove previous comments
            for (int idx = list.getChildCount() - 1; idx >= 3; idx--)
                list.removeViewAt(idx);

            for (int idx = 0; idx < adapter.getItemCount(); idx++) {
                CommentsAdapter.CommentView view = adapter.onCreateViewHolder(list, 0);
                adapter.onBindViewHolder(view, idx);
                list.addView(view.itemView);
            }

            adapter.setCommentActionListener(this);
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
                .subscribe(infoLineView::setTags, defaultOnError());
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

    @Override
    public boolean onCommentVoteClicked(Post.Comment comment, Vote vote) {
        return doIfAuthorized(this, () -> {
            bindFragment(this, voteService.vote(comment, vote))
                    .subscribe(Actions.empty(), defaultOnError());
        });
    }

    @Override
    public void onAnswerClicked(Post.Comment comment) {
        Runnable retry = () -> onAnswerClicked(comment);

        doIfAuthorized(this, () -> {
            NewCommentDialogFragment
                    .newInstance(Optional.fromNullable(comment))
                    .show(getChildFragmentManager(), null);

        }, retry);
    }

    @Override
    public void onCommentAuthorClicked(Post.Comment comment) {
        onUserClicked(comment.getName());
    }

    @Override
    public void onAddNewCommment(long parentId, String text) {
        bindFragment(this, voteService.postComment(feedItem, parentId, text))
                .lift(busyDialog(this))
                .subscribe(this::displayComments, defaultOnError());
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
}
