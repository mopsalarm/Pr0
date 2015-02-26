package com.pr0gramm.app;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pr0gramm.app.feed.AbstractFeedAdapter;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedService;
import com.squareup.picasso.Picasso;

import javax.inject.Inject;

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;

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

        FeedItem item = feed.getItem(idx);
        viewUsername.setText(item.getItem().getUser());
        viewRating.setText(String.valueOf(item.getItem().getUp()));

        // load the image
        String image = item.getItem().getImage();
        image = "http://img.pr0gramm.com/" + image;
        picasso.load(image)
                .resize(1024, 1024)
                .centerInside()
                .onlyScaleDown()
                .into(viewImage);
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
