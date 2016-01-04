package com.pr0gramm.app.ui.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.common.base.Optional;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.pr0gramm.response.Tag;
import com.pr0gramm.app.feed.Feed;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedLoader;
import com.pr0gramm.app.feed.FeedService;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.ui.FilterFragment;
import com.pr0gramm.app.ui.IdFragmentStatePagerAdapter;
import com.pr0gramm.app.ui.MainActionHandler;
import com.pr0gramm.app.ui.PreviewInfo;
import com.pr0gramm.app.ui.base.BaseFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import javax.inject.Inject;

import butterknife.Bind;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity;

/**
 */
public class PostPagerFragment extends BaseFragment implements FilterFragment {
    private static final Logger logger = LoggerFactory.getLogger("PostPagerFragment");

    private static final String ARG_FEED_PROXY = "PostPagerFragment.feedProxy";
    private static final String ARG_START_ITEM = "PostPagerFragment.startItem";
    private static final String ARG_START_ITEM_COMMENT = "PostPagerFragment.startItemComment";

    @Inject
    FeedService feedService;

    @Bind(R.id.pager)
    ViewPager viewPager;

    private Feed feed;
    private PostAdapter adapter;

    private PostFragment activePostFragment;

    private PreviewInfo previewInfo;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_post_pager, container, false);
    }

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // get the feed to show and setup a loader to load more data
        feed = getArgumentFeed(savedInstanceState);
        FeedLoader loader = new FeedLoader(FeedLoader.bindTo(this, this::onLoadError), feedService, feed);

        // create the adapter on the view
        adapter = new PostAdapter(getChildFragmentManager(), feed, loader) {
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

            viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageScrollStateChanged(int state) {
                    activity.getScrollHideToolbarListener().reset();
                    if (activePostFragment != null) {
                        activePostFragment.exitFullscreen();
                    }
                }
            });
        }

        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                if (position >= 0 && position + 1 < adapter.getCount()) {
                    Optional<Fragment> prev = adapter.getFragment(position);
                    Optional<Fragment> next = adapter.getFragment(position + 1);
                    if (prev.isPresent() && next.isPresent()) {
                        ((PostFragment) prev.get()).mediaHorizontalOffset(positionOffsetPixels / 2);
                        ((PostFragment) next.get()).mediaHorizontalOffset(
                                -viewPager.getWidth() / 2 + positionOffsetPixels / 2);
                    }
                }
            }
        });

        viewPager.setAdapter(adapter);

        if (savedInstanceState != null) {
            // calculate index of the first item to show if this is the first
            // time we show this fragment.
            FeedItem start = getArgumentStartItem(savedInstanceState);
            makeItemCurrent(start);
        }
    }

    private void onLoadError(Throwable throwable) {
        logger.warn("Could not load feed.", throwable);
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
        int index = feed.indexOf(item).or(0);

        logger.info("Moving to index: " + index);
        viewPager.setCurrentItem(index, false);
    }

    private void updateActiveItem(PostFragment newActiveFragment) {
        int position = adapter.getItemPosition(newActiveFragment);
        if (activePostFragment == newActiveFragment)
            return;

        logger.info("Setting feed item activate at " + position + " to " + newActiveFragment);

        // deactivate previous item
        if (activePostFragment != null)
            activePostFragment.setActive(false);

        // and activate the next one
        activePostFragment = newActiveFragment;
        if (activePostFragment != null) {
            activePostFragment.setActive(true);

            // try scroll to initial comment. This will only work if the comment
            // is a part of the given post and will otherwise do nothing
            long startCommentId = getArguments().getLong(ARG_START_ITEM_COMMENT);
            if (startCommentId > 0) {
                activePostFragment.autoScrollToComment(startCommentId);
            }

            if (previewInfo != null) {
                if (activePostFragment.getFeedItem().getId() == getArgumentStartItem(null).getId()) {
                    activePostFragment.setPreviewInfo(previewInfo);
                    previewInfo = null;
                }
            }
        }
    }

    /**
     * Gets the feed from the saved state. If there is no state
     * or it does not contain the feed proxy, the feed proxy is extracted
     * from {@link #getArguments()}
     *
     * @param savedState An optional saved state.
     */
    private Feed getArgumentFeed(@Nullable Bundle savedState) {
        Bundle encoded = null;
        if (savedState != null)
            encoded = savedState.getBundle(ARG_FEED_PROXY);

        if (encoded == null)
            encoded = getArguments().getBundle(ARG_FEED_PROXY);

        return Feed.restore(checkNotNull(encoded, "No feed-proxy found"));
    }

    /**
     * @see #getArgumentFeed(Bundle)
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
    @Override
    public FeedFilter getCurrentFilter() {
        // prevent errors here
        if (feed == null)
            return new FeedFilter();

        return feed.getFeedFilter();
    }

    public void onTagClicked(Tag tag) {
        ((MainActionHandler) getActivity()).onFeedFilterSelected(
                getCurrentFilter().withTags(tag.getTag()));
    }

    public void onUsernameClicked(String username) {
        // always show all uploads of a user.
        FeedFilter newFilter = getCurrentFilter()
                .withFeedType(FeedType.NEW)
                .withUser(username);

        ((MainActionHandler) getActivity()).onFeedFilterSelected(newFilter);
    }

    private void saveStateToBundle(Bundle outState) {
        if (viewPager != null && feed != null) {
            int position = viewPager.getCurrentItem();
            FeedItem item = feed.at(position);
            outState.putParcelable(ARG_START_ITEM, item);
            outState.putParcelable(ARG_FEED_PROXY, feed.persist(position));
        }
    }

    /**
     * Sets the pixels that should be used in the transition.
     */
    public void setPreviewInfo(PreviewInfo previewInfo) {
        this.previewInfo = previewInfo;
    }

    private static class PostAdapter extends IdFragmentStatePagerAdapter implements Feed.FeedListener {
        private final Feed proxy;
        private final FeedLoader loader;

        public PostAdapter(FragmentManager fragmentManager, Feed proxy, FeedLoader loader) {
            super(fragmentManager);

            this.proxy = proxy;
            this.proxy.setFeedListener(this);
            this.loader = loader;
        }

        @Override
        public Fragment getItem(int position) {
            if (!loader.isLoading()) {
                if (position > proxy.size() - 12) {
                    logger.info("requested pos=" + position + ", load next page");
                    loader.next();
                }

                if (position < 12) {
                    logger.info("requested pos=" + position + ", load prev page");
                    loader.previous();
                }
            }

            return PostFragment.newInstance(proxy.at(position));
        }

        @Override
        public int getCount() {
            return proxy.size();
        }

        @Override
        public int getItemPosition(Object object) {
            FeedItem item = ((PostFragment) object).getFeedItem();
            return proxy.indexOf(item).or(PagerAdapter.POSITION_NONE);
        }

        @Override
        protected long getItemId(int position) {
            return proxy.at(position).getId();
        }

        @Override
        public void onNewItems(List<FeedItem> newItems) {
            notifyDataSetChanged();
        }

        @Override
        public void onRemoveItems() {
            // should not happen
            throw new UnsupportedOperationException();
        }
    }

    public static PostPagerFragment newInstance(Feed feed, int idx, Optional<Long> commentId) {
        Bundle arguments = new Bundle();
        arguments.putBundle(ARG_FEED_PROXY, feed.persist(idx));
        arguments.putParcelable(ARG_START_ITEM, feed.at(idx));
        arguments.putLong(ARG_START_ITEM_COMMENT, commentId.or(-1L));

        PostPagerFragment fragment = new PostPagerFragment();
        fragment.setArguments(arguments);
        return fragment;
    }
}
