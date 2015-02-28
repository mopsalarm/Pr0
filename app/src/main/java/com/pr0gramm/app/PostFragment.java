package com.pr0gramm.app;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
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
import com.squareup.picasso.Downloader;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;
import rx.Observable;

import static rx.android.observables.AndroidObservable.bindFragment;

/**
 * This fragment shows the content of one post.
 */
public class PostFragment extends RoboFragment {
    private static final String ARG_FEED_ITEM = "PostFragment.Post";
    private static final long INFO_LINE_VIEW_ID = -1;
    private static final long PLAYER_VIEW_ID = -2;

    private FeedItem feedItem;

    @Inject
    private Picasso picasso;

    @Inject
    private FeedService feedService;

    @InjectView(R.id.list)
    private RecyclerView recyclerView;

//    @InjectView(R.id.image)
//    private ImageView viewImage;
//
//    @InjectView(R.id.username)
//    private UsernameView viewUsername;
//
//    @InjectView(R.id.rating)
//    private TextView viewRating;
//
//    @InjectView(R.id.tag_container)
//    private ViewGroup viewTagContainer;
//
//    @InjectView(R.id.video)
//    private VideoView viewVideo;
//
//    @InjectView(R.id.comments)
//    private RecyclerView viewComments;
//
//    @InjectView(R.id.progress)
//    private ProgressBar viewProgress;

    @Inject
    private Downloader downloader;

    private GenericAdapter adapter;
    private InfoLineView infoLineView;
    private PlayerView player;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        feedItem = getArguments().getParcelable(ARG_FEED_ITEM);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // TODO We want to do something like that later, but we need to handle
        // the click in the main activity.
        // ActionBarActivity activity = (ActionBarActivity) getActivity();
        // ActionBar actionBar = activity.getSupportActionBar();
        // actionBar.setDisplayHomeAsUpEnabled(true);

        return inflater.inflate(R.layout.fragment_post, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

//        viewComments.setAdapter(new CommentAdapter(Collections.<Post.Comment>emptyList()));
//        viewComments.setLayoutManager(new WrapContentLinearLayoutManager(getActivity(),
//                LinearLayoutManager.VERTICAL, false));
//
//        viewUsername.setUsername(feedItem.getUser(), feedItem.getMark());
//        viewRating.setText(String.valueOf(feedItem.getUp() - feedItem.getDown()));
//
//        if (outerScrollView != null && getActivity() instanceof MainActivity) {
//            MainActivity activity = (MainActivity) getActivity();
//            activity.onScrollHideToolbarListener.reset();
//
//            outerScrollView.setOnScrollListener((oldTop, top) ->
//                    activity.onScrollHideToolbarListener.onScrolled(top - oldTop));
//        }

        recyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                // forward scrolling events
                FragmentActivity activity = getActivity();
                if (activity instanceof MainActivity)
                    ((MainActivity) activity).onScrollHideToolbarListener.onScrolled(dy);
            }
        });

        adapter = new GenericAdapter();
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        addPlayerView();
        addInfoLineView();

        // load post info (comments and tags)
        long id = feedItem.getId();
        bindFragment(this, feedService.loadPostDetails(id)).subscribe(this::onPostReceived);
    }

    @Override
    public void onStart() {
        super.onStart();
        player.onStart();
    }

    @Override
    public void onPause() {
        player.onPause();
        super.onPause();
    }

    private void addInfoLineView() {
        ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        infoLineView = new InfoLineView(getActivity(), feedItem);
        infoLineView.setLayoutParams(params);
        adapter.add(new StaticViewType(INFO_LINE_VIEW_ID, infoLineView), null);
    }

    private void addPlayerView() {
        // get the url of the posts content (image or video)
        player = new PlayerView(getActivity(), picasso, downloader) {
            @Override
            protected <T> Observable<T> bind(Observable<T> observable) {
                return bindFragment(PostFragment.this, observable);
            }
        };

        // set correct the margin for the post fragment.
        ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        params.topMargin = AndroidUtility.getActionBarSize(getActivity());
        player.setLayoutParams(params);

        // initialize the player
        String url = "http://img.pr0gramm.com/" + feedItem.getImage();
        player.play(url);

        adapter.add(new StaticViewType(PLAYER_VIEW_ID, player) {
            @Override
            public RecyclerView.ViewHolder newViewHolder(ViewGroup parent) {
                RecyclerView.ViewHolder holder = super.newViewHolder(parent);
                holder.setIsRecyclable(false);
                return holder;
            }
        }, null);
    }

    private void onPostReceived(Post post) {
        List<Post.Comment> comments = sort(post.getComments());
        adapter.addAll(new CommentViewType(comments), comments);

        // update tags from post
        infoLineView.setTags(post.getTags());
    }

    private static class StaticViewType implements GenericAdapter.ViewType {
        private final long id;
        private final View view;

        private StaticViewType(long id, View view) {
            this.id = id;
            this.view = view;
        }

        @Override
        public long getId(Object object) {
            return id;
        }

        @Override
        public RecyclerView.ViewHolder newViewHolder(ViewGroup parent) {
            // just return the view as is.
            return new RecyclerView.ViewHolder(view) {
            };
        }

        @Override
        public void bind(RecyclerView.ViewHolder holder, Object object) {
            // do nothing on bind.
        }
    }

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
