package com.pr0gramm.app;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import com.google.common.collect.Ordering;
import com.google.common.io.Resources;
import com.pr0gramm.app.api.Post;
import com.pr0gramm.app.feed.AbstractFeedAdapter;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedService;
import com.pr0gramm.app.feed.Vote;
import com.squareup.picasso.Picasso;

import java.net.URL;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import pl.droidsonroids.gif.GifDrawable;
import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

import static rx.android.observables.AndroidObservable.bindFragment;

/**
 * This fragment shows the content of one post.
 */
public class PostFragment extends RoboFragment {
    @Inject
    private Picasso picasso;

    @Inject
    private FeedService feedService;

    @InjectView(R.id.image)
    private ImageView viewImage;

    @InjectView(R.id.username)
    private TextView viewUsername;

    @InjectView(R.id.rating)
    private TextView viewRating;

    @InjectView(R.id.tag_container)
    private ViewGroup viewTagContainer;

    @InjectView(R.id.video)
    private VideoView viewVideo;

    @InjectView(R.id.comments)
    private RecyclerView viewComments;

    @InjectView(R.id.action_rate_plus)
    private TextView viewActionRatePlus;

    @InjectView(R.id.action_rate_minus)
    private TextView viewActionRateMinus;

    @Nullable
    @InjectView(R.id.scroll)
    private VerticalScrollView outerScrollView;

    private AbstractFeedAdapter<?> feed;
    private int idx;

    public PostFragment() {
        setRetainInstance(true);
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

        if (feed == null)
            return;

        viewComments.setAdapter(new CommentAdapter(Collections.<Post.Comment>emptyList()));
        viewComments.setLayoutManager(new WrapContentLinearLayoutManager(getActivity(),
                LinearLayoutManager.VERTICAL, false));

        FeedItem item = feed.getItem(idx);
        viewUsername.setText(item.getItem().getUser());
        viewRating.setText(String.valueOf(item.getItem().getUp()));

        if (outerScrollView != null && getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            activity.onScrollHideToolbarListener.reset();

            outerScrollView.setOnScrollListener((oldTop, top) ->
                    activity.onScrollHideToolbarListener.onScrolled(top - oldTop));
        }

        // load the image
        String image = "http://img.pr0gramm.com/" + item.getItem().getImage();
        if (image.endsWith(".webm")) {
            // hide the image view
            viewImage.setVisibility(View.GONE);
            viewVideo.setVisibility(View.VISIBLE);

            // hide video controls
            MediaController ctrl = new MediaController(getActivity());
            ctrl.setVisibility(View.GONE);
            viewVideo.setMediaController(ctrl);

            viewVideo.setVideoURI(Uri.parse(image));
            viewVideo.start();
            viewVideo.setOnCompletionListener(mp -> {
                // start from the beginning
                viewVideo.seekTo(0);
                viewVideo.start();
            });

            viewVideo.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                int lastWidth = 0;

                @Override
                public boolean onInfo(MediaPlayer mp, int what, int extra) {
                    int width = mp.getVideoWidth();
                    int height = mp.getVideoHeight();

                    // check if size is okay
                    if (width <= 0 || width == lastWidth)
                        return true;

                    // remember!
                    lastWidth = width;

                    ViewParent parent = viewVideo.getParent();
                    if (parent instanceof View) {
                        int parentWidth = ((View) parent).getWidth();
                        float aspect = width / (float) height;
                        viewVideo.getLayoutParams().height = (int) (parentWidth / aspect);
                        viewVideo.getParent().requestLayout();
                    }

                    return true;
                }
            });
        } else if (image.endsWith(".gif")) {
            // hide the video player
            viewVideo.stopPlayback();
            viewVideo.setVisibility(View.GONE);
            viewImage.setVisibility(View.VISIBLE);

            bindFragment(this, Async.fromCallable(() -> {
                // TODO Check if we are still at the same post cd after loading
                byte[] bytes = Resources.toByteArray(new URL(image));
                return new GifDrawable(bytes);
            }, Schedulers.io())).subscribe(viewImage::setImageDrawable);

        } else {
            // hide the video player
            viewVideo.stopPlayback();
            viewVideo.setVisibility(View.GONE);
            viewImage.setVisibility(View.VISIBLE);

            picasso.load(image)
                    .resize(1024, 1024)
                    .centerInside()
                    .onlyScaleDown()
                    .into(viewImage);
        }

        long id = item.getItem().getId();
        bindFragment(this, feedService.loadPostDetails(id)).subscribe(this::onPostReceived);
    }

    private void onPostReceived(Post post) {
        // TODO check if we are still showing exactly this post!
        // TODO use recyclerView for tags

        List<Post.Tag> tags = Ordering.natural()
                .reverse()
                .onResultOf(Post.Tag::getConfidence)
                .sortedCopy(post.getTags());

        // remove previous tags
        for (int i = viewTagContainer.getChildCount() - 1; i >= 1; i--)
            viewTagContainer.removeViewAt(i);

        LayoutInflater inflater = LayoutInflater.from(getActivity());
        for (Post.Tag tag : tags) {
            TextView view = (TextView) inflater.inflate(R.layout.tag, viewTagContainer, false);
            view.setText(tag.getTag());

            viewTagContainer.addView(view);
        }

        // the comments
        viewComments.setAdapter(new CommentAdapter(post.getComments()));
    }

    public static PostFragment newInstance(AbstractFeedAdapter<?> feed, int idx) {
        PostFragment fragment = new PostFragment();
        fragment.initialize(feed, idx);

        return fragment;
    }

    private void initialize(AbstractFeedAdapter<?> feed, int idx) {
        this.feed = feed;
        this.idx = idx;
    }
}
