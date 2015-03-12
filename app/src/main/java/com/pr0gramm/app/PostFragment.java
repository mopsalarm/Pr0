package com.pr0gramm.app;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.pr0gramm.app.api.Post;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedService;
import com.pr0gramm.app.feed.Vote;
import com.pr0gramm.app.viewer.ViewerFragment;

import java.util.List;

import javax.inject.Inject;

import roboguice.inject.InjectView;
import rx.Observable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.pr0gramm.app.BusyDialogFragment.busyDialog;
import static com.pr0gramm.app.ErrorDialogFragment.errorDialog;
import static com.pr0gramm.app.LoginDialogFragment.doIfAuthorized;
import static com.pr0gramm.app.ScrollHideToolbarListener.ToolbarActivity;
import static rx.android.observables.AndroidObservable.bindFragment;

/**
 * This fragment shows the content of one post.
 */
public class PostFragment extends NestingFragment implements NewTagDialogFragment.OnAddNewTagsListener {
    private static final String ARG_FEED_ITEM = "PostFragment.post";

    private boolean active;
    private FeedItem feedItem;
    private ViewerFragment viewer;

    @Inject
    private FeedService feedService;

    @Inject
    private VoteService voteService;

    @Inject
    private Settings settings;

    @Inject
    private SeenService seenService;

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        loadPostDetails();
    }

    @Override
    public void onStart() {
        super.onStart();
        viewer.onStart();

        if (active) {
            onMarkedActive();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        viewer.onResume();
    }

    @Override
    public void onPause() {
        viewer.onPause();
        super.onPause();
    }

    @Override
    public void onStop() {
        viewer.onStop();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        viewer.onDestroy();
        super.onDestroy();
    }

    /**
     * Loads the information about the post. This includes the
     * tags and the comments.
     */
    private void loadPostDetails() {
        Observable<Post> details = feedService.loadPostDetails(feedItem.getId());
        bindFragment(this, details)
                .lift(errorDialog(this))
                .subscribe(this::onPostReceived);
    }

    private void initializeInfoLine() {
        // get the vote from the service
        Observable<Vote> cachedVote = voteService.getVote(feedItem);

        // display the feed item in the view
        infoLineView.setFeedItem(feedItem, bindFragment(this, cachedVote));

        infoLineView.setOnTagClickedListener(tag -> {
            ((MainActivity) getActivity()).onTagClicked(tag);
        });

        // register the vote listener
        infoLineView.setOnVoteListener(vote -> {
            Runnable action = () -> {
                bindFragment(this, voteService.vote(feedItem, vote))
                        .lift(errorDialog(this))
                        .subscribe();
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
        ViewerFragment.Binder binder = new ViewerFragment.Binder() {
            @Override
            public <T> Observable<T> bind(Observable<T> observable) {
                return bindFragment(PostFragment.this, observable).lift(errorDialog(PostFragment.this));
            }

            @Override
            public void onError(String text) {
                ErrorDialogFragment.showErrorString(getChildFragmentManager(), text);
            }
        };

        // initialize a new viewer fragment
        viewer = ViewerFragment.newInstance(getActivity(), binder, feedItem);
        viewerContainer.addView(viewer);
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

        bindFragment(this, voteService.getVotes(post.getComments())).subscribe(votes -> {
            // TODO Think of something nicer for the comments :/
            // remove previous comments
            for (int idx = list.getChildCount() - 1; idx >= 2; idx--)
                list.removeViewAt(idx);

            // and display the comments
            CommentsAdapter adapter = new CommentsAdapter(post.getComments());
            adapter.setVoteCache(votes);

            for (int idx = 0; idx < adapter.getItemCount(); idx++) {
                CommentsAdapter.CommentView view = adapter.onCreateViewHolder(list, 0);
                adapter.onBindViewHolder(view, idx);

                list.addView(view.itemView);
            }

            adapter.setOnCommentVoteClickedListener((comment, vote) -> doIfAuthorized(this, () -> {
                bindFragment(this, voteService.vote(comment, vote))
                        .lift(errorDialog(this))
                        .subscribe();
            }));
        });
    }

    /**
     * Returns the feed item that is displayed in this {@link com.pr0gramm.app.PostFragment}.
     */
    public FeedItem getFeedItem() {
        return feedItem;
    }

    /**
     * Called from the {@link com.pr0gramm.app.PostPagerFragment} if this fragment
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
        seenService.markAsSeen(feedItem);
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
                .lift(errorDialog(this))
                .lift(busyDialog(this))
                .subscribe(infoLineView::setTags);
    }

    /**
     * Creates a new instance of a {@link com.pr0gramm.app.PostFragment} displaying the
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
}
