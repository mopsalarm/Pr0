package com.pr0gramm.app;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import com.google.common.collect.Ordering;
import com.google.common.io.ByteStreams;
import com.pr0gramm.app.api.Post;
import com.pr0gramm.app.feed.FeedProxy;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedService;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.Picasso;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import pl.droidsonroids.gif.GifDrawable;
import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

import static rx.android.observables.AndroidObservable.bindFragment;

/**
 * This fragment shows the content of one post.
 */
public class PostFragment extends RoboFragment {
    private static final String ARG_FEED_ITEM = "PostFragment.Post";

    private FeedItem feedItem;

    @Inject
    private Picasso picasso;

    @Inject
    private FeedService feedService;

    @InjectView(R.id.image)
    private ImageView viewImage;

    @InjectView(R.id.username)
    private UsernameView viewUsername;

    @InjectView(R.id.rating)
    private TextView viewRating;

    @InjectView(R.id.tag_container)
    private ViewGroup viewTagContainer;

    @InjectView(R.id.video)
    private VideoView viewVideo;

    @InjectView(R.id.comments)
    private RecyclerView viewComments;

    @InjectView(R.id.progress)
    private ProgressBar viewProgress;

    @Inject
    private Downloader downloader;

    @Nullable
    @InjectView(R.id.scroll)
    private VerticalScrollView outerScrollView;

    private Runnable onStart, onStop;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // do nothing on start/stop per default
        onStart = onStop = () -> {
        };

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

        viewComments.setAdapter(new CommentAdapter(Collections.<Post.Comment>emptyList()));
        viewComments.setLayoutManager(new WrapContentLinearLayoutManager(getActivity(),
                LinearLayoutManager.VERTICAL, false));

        viewUsername.setUsername(feedItem.getUser(), feedItem.getMark());
        viewRating.setText(String.valueOf(feedItem.getUp() - feedItem.getDown()));

        if (outerScrollView != null && getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            activity.onScrollHideToolbarListener.reset();

            outerScrollView.setOnScrollListener((oldTop, top) ->
                    activity.onScrollHideToolbarListener.onScrolled(top - oldTop));
        }

        // get the url of the posts content (image or video)
        String url = "http://img.pr0gramm.com/" + feedItem.getImage();
        if (url.toLowerCase().endsWith(".webm")) {
            // hide the image view
            viewImage.setVisibility(View.GONE);
            viewVideo.setVisibility(View.VISIBLE);

            displayTypeVideo(url);

        } else {
            // hide the video view
            viewVideo.setVisibility(View.GONE);
            viewImage.setVisibility(View.VISIBLE);

            if (url.toLowerCase().endsWith(".gif")) {
                displayTypeGif(url);
            } else {
                displayTypeImage(url);
            }
        }

        // load post info (comments and tags)
        long id = feedItem.getId();
        bindFragment(this, feedService.loadPostDetails(id)).subscribe(this::onPostReceived);
    }

    /**
     * Displays the given image url. This will scale down the image to
     * fit in the ui.
     *
     * @param image The image to load and display.
     */
    private void displayTypeImage(String image) {
        picasso.load(image)
                .resize(1024, 1024)
                .centerInside()
                .onlyScaleDown()
                .into(viewImage);
    }

    /**
     * Loads and displays a gif file.
     *
     * @param image The gif file to load.
     */
    @SuppressLint("NewApi")
    private void displayTypeGif(String image) {
        Observable<GifDrawable> loader = Async.fromCallable(() -> {
            // load the gif file
            Downloader.Response response = downloader.load(Uri.parse(image), 0);
            try (InputStream stream = response.getInputStream()) {
                // and decode it
                return new GifDrawable(ByteStreams.toByteArray(stream));
            }
        }, Schedulers.io());

        // show progress bar while loading
        viewProgress.setVisibility(View.VISIBLE);

        bindFragment(this, loader).subscribe(gif -> {
            // and set gif on ui thread as drawable
            viewProgress.setVisibility(View.GONE);

            onStart = () -> viewImage.setImageDrawable(gif);
            onStop = () -> viewImage.setImageDrawable(null);

            // and do it once now.
            onStart.run();
        });
    }

    private void displayTypeVideo(String image) {
        // hide video controls
        MediaController ctrl = new MediaController(getActivity());
        ctrl.setVisibility(View.GONE);
        viewVideo.setMediaController(ctrl);

        // set video on view
        viewVideo.setVideoURI(Uri.parse(image));

        // start on play
        viewVideo.setOnClickListener(v -> {
            viewVideo.seekTo(0);
        });

        viewVideo.setOnPreparedListener(mp -> {
            mp.setLooping(true);

            int width = mp.getVideoWidth();
            int height = mp.getVideoHeight();

            ViewParent parent = viewVideo.getParent();
            if (parent instanceof View) {
                int parentWidth = ((View) parent).getWidth();
                float aspect = width / (float) height;

                ViewGroup.LayoutParams params = viewVideo.getLayoutParams();
                params.height = (int) (parentWidth / aspect);
                viewVideo.setLayoutParams(params);
            }
        });

        onStart = () -> {
            viewVideo.seekTo(0);
            viewVideo.start();
        };

        onStop = viewVideo::pause;
    }

    @Override
    public void onResume() {
        super.onResume();
        onStart.run();
    }

    @Override
    public void onPause() {
        onStop.run();
        super.onPause();
    }

    private void onPostReceived(Post post) {
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

        // TODO the comments
        // viewComments.setAdapter(new CommentAdapter(post.getComments()));
    }

    public static PostFragment newInstance(FeedItem item) {
        Bundle arguments = new Bundle();
        arguments.putParcelable(ARG_FEED_ITEM, item);

        PostFragment fragment = new PostFragment();
        fragment.setArguments(arguments);

        return fragment;
    }
}
