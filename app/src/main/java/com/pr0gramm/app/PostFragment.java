package com.pr0gramm.app;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import static com.pr0gramm.app.BusyDialogFragment.busyDialog;
import static com.pr0gramm.app.ErrorDialogFragment.errorDialog;
import static com.pr0gramm.app.LoginDialogFragment.doIfAuthorized;
import static rx.android.observables.AndroidObservable.bindFragment;

/**
 * This fragment shows the content of one post.
 */
public class PostFragment extends NestingFragment implements NewTagDialogFragment.OnNewTagListener {
    private static final String ARG_FEED_ITEM = "PostFragment.Post";

    private FeedItem feedItem;

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
    private ViewerFragment viewer;
    private boolean active;

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

        scrollView.setOnScrollListener((oldTop, top) -> {
            int dy = top - oldTop;
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.onScrollHideToolbarListener.onScrolled(dy);
        });

        swipeRefreshLayout.setOnRefreshListener(this::startLoadingInfo);

        // use height of the toolbar to configure swipe refresh layout.
        int abHeight = AndroidUtility.getActionBarSize(getActivity());
        swipeRefreshLayout.setProgressViewOffset(false, 0, (int) (1.5 * abHeight));
        swipeRefreshLayout.setColorSchemeResources(R.color.primary);

        // TODO Think of something nicer for the comments
        // commentsView.setLayoutManager(new WrapContentLinearLayoutManager(getActivity(),
        //         LinearLayoutManager.VERTICAL, false));

        initializePlayerFragment();
        initializeInfoLine();

        startLoadingInfo();
        Log.i("PostFragment", "OnCreateView called " + this);
    }

    /**
     * Loads the information about the post. This includes the
     * tags and the comments.
     */
    private void startLoadingInfo() {
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
            NewTagDialogFragment dialog = NewTagDialogFragment.newInstance(feedItem);
            dialog.show(getChildFragmentManager(), null);
        });
    }

    private void initializePlayerFragment() {
        //check if the fragment already exists.
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.player_container);
        if (fragment != null) {
            Log.i("PostFragment", "Player fragment found");
            viewer = (ViewerFragment) fragment;
            return;
        }

        // initialize the viewer
        String url = "http://img.pr0gramm.com/" + feedItem.getImage();
        viewer = ViewerFragment.newInstance(settings, url);

        // and add the player to the view.
        getChildFragmentManager().beginTransaction()
                .add(R.id.player_container, viewer)
                .commit();
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

    private void onMarkedInactive() {
        viewer.stopMedia();
    }

    private void onMarkedActive() {
        seenService.markAsSeen(feedItem);
        viewer.playMedia();
    }

    @Override
    public void onStart() {
        super.onStart();

        if (active)
            onMarkedActive();
    }

    @Override
    public void onNewTags(List<String> tags) {
        bindFragment(this, voteService.tag(feedItem, tags))
                .lift(errorDialog(this))
                .lift(busyDialog(this))
                .subscribe(infoLineView::setTags);
    }

    public static PostFragment newInstance(FeedItem item) {
        Bundle arguments = new Bundle();
        arguments.putParcelable(ARG_FEED_ITEM, item);

        PostFragment fragment = new PostFragment();
        fragment.setArguments(arguments);

        return fragment;
    }
}
