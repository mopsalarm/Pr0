package com.pr0gramm.app;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.pr0gramm.app.api.Post;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedService;
import com.pr0gramm.app.viewer.ViewerFragment;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import roboguice.inject.InjectView;
import rx.Observable;

import static com.pr0gramm.app.ErrorDialogFragment.errorDialog;
import static com.pr0gramm.app.LoginDialogFragment.doIfAuthorized;
import static rx.android.observables.AndroidObservable.bindFragment;

/**
 * This fragment shows the content of one post.
 */
public class PostFragment extends NestingFragment {
    private static final String ARG_FEED_ITEM = "PostFragment.Post";

    private FeedItem feedItem;

    @Inject
    private FeedService feedService;

    @Inject
    private VoteService voteService;

    @InjectView(R.id.comments)
    private RecyclerView commentsView;

    @InjectView(R.id.refresh)
    private SwipeRefreshLayout swipeRefreshLayout;

    @InjectView(R.id.info_line)
    private InfoLineView infoLineView;

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

//        FragmentActivity activity = getActivity();
//        if (activity instanceof MainActivity) {
//            commentsView.setOnScrollListener(new RecyclerView.OnScrollListener() {
//                @Override
//                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
//                    // forward scrolling events
//                    ((MainActivity) activity).onScrollHideToolbarListener.onScrolled(dy);
//                }
//            });
//        }

        swipeRefreshLayout.setOnRefreshListener(this::startLoadingInfo);

        // use height of the toolbar to configure swipe refresh layout.
        int abHeight = AndroidUtility.getActionBarSize(getActivity());
        swipeRefreshLayout.setProgressViewOffset(false, 0, (int) (1.5 * abHeight));
        swipeRefreshLayout.setColorSchemeResources(R.color.primary);

        // FIXME initialize adapter for views
        // adapter = new GenericAdapter();
        // commentsView.setAdapter(adapter);
        commentsView.setLayoutManager(new LinearLayoutManager(getActivity()));

        initializePlayerFragment();
        initializeInfoLine();

        startLoadingInfo();
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
        // display the feed item in the view
        infoLineView.setFeedItem(feedItem);

        // register the vote listener
        VoteView voteView = infoLineView.getVoteView();
        voteView.setOnVoteListener(vote -> doIfAuthorized(PostFragment.this, () -> {
            bindFragment(this, voteService.vote(feedItem, vote))
                    .lift(errorDialog(this))
                    .subscribe();
        }));
    }

    private void initializePlayerFragment() {
        // check if the fragment already exists.
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.player_container);
        if (fragment != null)
            return;

        // initialize the viewer
        String url = "http://img.pr0gramm.com/" + feedItem.getImage();
        ViewerFragment viewer = ViewerFragment.newInstance(url);

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

        List<Post.Comment> comments = sort(post.getComments());
        // FIXME adapter.addAll(new CommentViewType(comments), comments);

    }

    public FeedItem getFeedItem() {
        return feedItem;
    }

//    private static class StaticViewType implements GenericAdapter.ViewType {
//        private final long id;
//        private final View view;
//
//        private StaticViewType(long id, View view) {
//            this.id = id;
//            this.view = view;
//        }
//
//        @Override
//        public long getId(Object object) {
//            return id;
//        }
//
//        @Override
//        public RecyclerView.ViewHolder newViewHolder(ViewGroup parent) {
//            // just return the view as is.
//            return new RecyclerView.ViewHolder(view) {
//            };
//        }
//
//        @Override
//        public void bind(RecyclerView.ViewHolder holder, Object object) {
//            // do nothing on bind.
//        }
//    }

    public static PostFragment newInstance(FeedItem item) {
        Bundle arguments = new Bundle();
        arguments.putParcelable(ARG_FEED_ITEM, item);

        PostFragment fragment = new PostFragment();
        fragment.setArguments(arguments);

        return fragment;
    }

    /**
     * "Flattens" a list of hierarchical comments to a sorted list of comments.
     *
     * @param comments The comments to sort
     */
    public static List<Post.Comment> sort(List<Post.Comment> comments) {
        ImmutableListMultimap<Integer, Post.Comment> byParent =
                Multimaps.index(comments, Post.Comment::getParent);

        ArrayList<Post.Comment> result = new ArrayList<>();
        appendChildComments(result, byParent, 0);
        return result;
    }

    private static void appendChildComments(List<Post.Comment> target,
                                            ListMultimap<Integer, Post.Comment> byParent, int id) {

        List<Post.Comment> children = COMMENT_BY_CONFIDENCE.sortedCopy(byParent.get(id));
        for (Post.Comment child : children) {
            target.add(child);
            appendChildComments(target, byParent, child.getId());
        }
    }

    private static final Ordering<Post.Comment> COMMENT_BY_CONFIDENCE =
            Ordering.natural().reverse().onResultOf(Post.Comment::getConfidence);
}
