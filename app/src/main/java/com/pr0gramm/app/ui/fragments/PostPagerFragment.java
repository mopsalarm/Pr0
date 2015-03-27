package com.pr0gramm.app.ui.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.inject.Inject;
import com.pr0gramm.app.IdFragmentStatePagerAdapter;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.response.Tag;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedProxy;
import com.pr0gramm.app.feed.FeedService;
import com.pr0gramm.app.ui.MainActionHandler;

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity;

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
    private PostAdapter adapter;

    private PostFragment activePostFragment;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_post_pager, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // create the proxy and use it as the source for the view pager.
        proxy = getArgumentFeedProxy(savedInstanceState);
        proxy.setLoader(new FeedProxy.FragmentFeedLoader(this, feedService));

        // create the adapter on the view
        adapter = new PostAdapter(getChildFragmentManager(), proxy) {
            @Override
            public void setPrimaryItem(ViewGroup container, int position, Object object) {
                super.setPrimaryItem(container, position, object);
                updateActiveItem((PostFragment) object);
                saveStateToBundle(getArguments());
            }
        };

        if (getActivity() instanceof ToolbarActivity) {
            ToolbarActivity activity = (ToolbarActivity) getActivity();
            activity.getScrollHideToolbarListener().reset();

            viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageScrollStateChanged(int state) {
                    activity.getScrollHideToolbarListener().reset();
                }
            });
        }

        viewPager.setAdapter(adapter);
        Log.i("PostPager", "state is " + savedInstanceState);

        if (savedInstanceState != null) {
            // calculate index of the first item to show if this is the first
            // time we show this fragment.
            FeedItem start = getArgumentStartItem(savedInstanceState);
            makeItemCurrent(start);
        }
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        // calculate index of the first item to show if this is the first
        // time we show this fragment.
        FeedItem start = getArgumentStartItem(savedInstanceState);
        makeItemCurrent(start);
    }

    private void makeItemCurrent(FeedItem item) {
        int index = proxy.getPosition(item).or(0);

        Log.i("PostPager", "Moving to index: " + index);
        viewPager.setCurrentItem(index, false);
    }

    private void updateActiveItem(PostFragment newActiveFragment) {
        int position = adapter.getItemPosition(newActiveFragment);
        if (activePostFragment == newActiveFragment)
            return;

        Log.i("PostPager", "Setting feed item activate at " + position + " to " + newActiveFragment);

        // deactivate previous item
        if (activePostFragment != null)
            activePostFragment.setActive(false);

        // and activate the next one
        activePostFragment = newActiveFragment;
        if (activePostFragment != null)
            activePostFragment.setActive(true);
    }

    /**
     * Gets the feed proxy from the saved state. If there is no state
     * or it does not contain the feed proxy, the feed proxy is extracted
     * from {@link #getArguments()}
     *
     * @param savedState An optional saved state.
     */
    private FeedProxy getArgumentFeedProxy(@Nullable Bundle savedState) {
        Bundle encoded = null;
        if (savedState != null)
            encoded = savedState.getBundle(ARG_FEED_PROXY);

        if (encoded == null)
            encoded = getArguments().getBundle(ARG_FEED_PROXY);

        return FeedProxy.fromBundle(checkNotNull(encoded, "No feed-proxy found"));
    }

    /**
     * @see #getArgumentFeedProxy(android.os.Bundle)
     */
    private FeedItem getArgumentStartItem(@Nullable Bundle saveState) {
        FeedItem result = null;

        if (saveState != null)
            result = saveState.getParcelable(ARG_START_ITEM);

        if (result == null)
            result = getArguments().getParcelable(ARG_START_ITEM);

        return checkNotNull(result, "No start-item found");
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        saveStateToBundle(outState);
    }

    /**
     * Returns the feed filter for this fragment.
     */
    public FeedFilter getCurrentFilter() {
        // prevent errors here
        if (proxy == null)
            return new FeedFilter();

        return proxy.getFeedFilter();
    }

    public void onTagClicked(Tag tag) {
        ((MainActionHandler) getActivity()).onFeedFilterSelected(
                getCurrentFilter().withTags(tag.getTag()));
    }

    public void onUsernameClicked(String username) {
        ((MainActionHandler) getActivity()).onFeedFilterSelected(
                getCurrentFilter().withUser(username));
    }

    private void saveStateToBundle(Bundle outState) {
        if (viewPager != null && proxy != null) {
            int position = viewPager.getCurrentItem();
            FeedItem item = proxy.getItemAt(position);
            outState.putParcelable(ARG_START_ITEM, item);
            outState.putParcelable(ARG_FEED_PROXY, proxy.toBundle(position));
        }
    }

    private static class PostAdapter extends IdFragmentStatePagerAdapter implements FeedProxy.OnChangeListener {
        private final FeedProxy proxy;

        public PostAdapter(FragmentManager fragmentManager, FeedProxy proxy) {
            super(fragmentManager);

            this.proxy = proxy;
            this.proxy.setOnChangeListener(this);
        }

        @Override
        public Fragment getItem(int position) {
            if (!proxy.isLoading()) {
                if (position > proxy.getItemCount() - 8) {
                    Log.i("PostPager", "requested pos=" + position + ", load next page");
                    proxy.loadNextPage();
                }

                if (position < 8) {
                    Log.i("PostPager", "requested pos=" + position + ", load prev page");
                    proxy.loadPreviousPage();
                }
            }

            return PostFragment.newInstance(proxy.getItemAt(position));
        }

        @Override
        public int getCount() {
            return proxy.getItemCount();
        }

        @Override
        public void onItemRangeInserted(int start, int count) {
            Log.i("PostPager", "Insert new posts at " + start);
            notifyDataSetChanged();
        }

        @Override
        public int getItemPosition(Object object) {
            FeedItem item = ((PostFragment) object).getFeedItem();
            return proxy.getPosition(item).or(PagerAdapter.POSITION_NONE);
        }

        @Override
        protected long getItemId(int position) {
            return proxy.getItemAt(position).getId();
        }

        @Override
        public void onItemRangeRemoved(int start, int count) {
            // should not happen
            throw new UnsupportedOperationException();
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
