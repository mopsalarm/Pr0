package com.pr0gramm.app;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedProxy;
import com.pr0gramm.app.feed.FeedService;

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;

/**
 */
public class PostPagerFragment extends RoboFragment {
    private static final String ARG_FEED_PROXY = "PostPagerFragment.feedProxy";
    private static final String ARG_START_ITEM = "PostPagerFragment.startItem";

    @Inject
    private FeedService feedService;

    @InjectView(R.id.pager)
    private ViewPager viewPager;

    private FeedProxy proxy;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_post_pager, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // use instance state if available.
        Bundle arguments = savedInstanceState != null ? savedInstanceState : getArguments();

        // re-use proxy if available
        Log.i("PostPager", "create new proxy for feed");
        proxy = FeedProxy.fromBundle(arguments.getBundle(ARG_FEED_PROXY));

        // bind the loader to this fragment.
        proxy.setLoader(new FeedProxy.FragmentFeedLoader(this, feedService));

        // set the adapter and show the correct post.
        FeedItem start = arguments.getParcelable(ARG_START_ITEM);
        PostAdapter adapter = new PostAdapter();
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(proxy.getPosition(start).or(0));

        proxy.setOnChangeListener(new FeedProxy.OnChangeListener() {
            @Override
            public void onItemRangeInserted(int start, int count) {
                adapter.notifyDataSetChanged();

                // check if we need to change the position of the view
                int current = viewPager.getCurrentItem();
                if (start < current) {
                    viewPager.setCurrentItem(current + count, false);
                }
            }

            @Override
            public void onItemRangeRemoved(int start, int count) {
                // shouldn't happen anyways
                adapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        int idx = viewPager.getCurrentItem();
        outState.putBundle(ARG_FEED_PROXY, proxy.toBundle(idx));
        outState.putParcelable(ARG_START_ITEM, proxy.getItemAt(idx));
    }

    private class PostAdapter extends FragmentStatePagerAdapter {
        public PostAdapter() {
            super(getChildFragmentManager());
        }

        @Override
        public Fragment getItem(int position) {
            if (position > proxy.getItemCount() - 12)
                proxy.loadNextPage();

            if (position < 12)
                proxy.loadPreviousPage();

            return PostFragment.newInstance(proxy.getItemAt(position));
        }

        @Override
        public int getCount() {
            return proxy.getItemCount();
        }
    }

    public static PostPagerFragment newInstance(FeedProxy proxy, int idx) {
        Bundle arguments = new Bundle();
        arguments.putBundle(ARG_FEED_PROXY, proxy.toBundle(idx));
        arguments.putParcelable(ARG_START_ITEM, proxy.getItemAt(idx));

        PostPagerFragment fragment = new PostPagerFragment();
        fragment.setArguments(arguments);
        return fragment;
    }
}
