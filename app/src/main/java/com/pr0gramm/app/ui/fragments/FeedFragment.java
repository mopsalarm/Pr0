package com.pr0gramm.app.ui.fragments;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.pr0gramm.app.AndroidUtility;
import com.pr0gramm.app.MergeRecyclerAdapter;
import com.pr0gramm.app.Pr0grammApplication;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.api.pr0gramm.Info;
import com.pr0gramm.app.feed.ContentType;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedProxy;
import com.pr0gramm.app.feed.FeedService;
import com.pr0gramm.app.services.BookmarkService;
import com.pr0gramm.app.services.SeenService;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.FeedFilterFormatter;
import com.pr0gramm.app.ui.MainActionHandler;
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment;
import com.pr0gramm.app.ui.dialogs.WritePrivateMessageDialog;
import com.pr0gramm.app.ui.views.BusyIndicator;
import com.pr0gramm.app.ui.views.CustomSwipeRefreshLayout;
import com.pr0gramm.app.ui.views.UserInfoCell;
import com.pr0gramm.app.ui.views.UserInfoFoundView;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;
import rx.Observable;
import rx.functions.Actions;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity;
import static com.pr0gramm.app.ui.ScrollHideToolbarListener.estimateRecyclerViewScrollY;
import static java.lang.Math.max;
import static rx.android.observables.AndroidObservable.bindFragment;

/**
 */
public class FeedFragment extends RoboFragment implements UserInfoCell.UserActionListener {
    private static final Logger logger = LoggerFactory.getLogger(FeedFragment.class);

    private static final String ARG_FEED_FILTER = "FeedFragment.filter";
    private static final String ARG_FEED_START = "FeedFragment.start";

    @Inject
    private FeedService feedService;

    @Inject
    private Picasso picasso;

    @Inject
    private SharedPreferences sharedPreferences;

    @Inject
    private SeenService seenService;

    @Inject
    private Settings settings;

    @InjectView(R.id.list)
    private RecyclerView recyclerView;

    @InjectView(R.id.progress)
    private BusyIndicator busyIndicator;

    @InjectView(R.id.refresh)
    private CustomSwipeRefreshLayout swipeRefreshLayout;

    @InjectView(R.id.empty)
    private View noResultsView;

    private MergeRecyclerAdapter<RecyclerView.Adapter<?>> adapter;
    private FeedAdapter feedAdapter;
    private GridLayoutManager layoutManager;
    private IndicatorStyle seenIndicatorStyle;

    @Inject
    private BookmarkService bookmarkService;

    @Inject
    private UserService userService;

    private boolean bookmarkable;
    private Optional<Long> autoOpenOnLoad = Optional.absent();
    private Optional<Long> autoScrollOnLoad = Optional.absent();

    private Observable<Info> userInfoObservable;
    private Function<Info, View> userInfoViewSupplier;

    /**
     * Initialize a new feed fragment.
     */
    public FeedFragment() {
        setHasOptionsMenu(true);
        setRetainInstance(true);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // initialize auto opening
        long startId = getArguments().getLong(ARG_FEED_START);
        autoOpenOnLoad = autoScrollOnLoad = Optional.fromNullable(startId > 0 ? startId : null);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (feedAdapter == null) {
            if (busyIndicator != null)
                busyIndicator.setVisibility(View.VISIBLE);

            // create a new adapter if necessary
            feedAdapter = newFeedAdapter();

        } else {
            updateNoResultsTextView();
            removeBusyIndicator();
        }

        seenIndicatorStyle = settings.seenIndicatorStyle();

        // prepare the list of items
        int columnCount = getThumbnailColumns();
        layoutManager = new GridLayoutManager(getActivity(), columnCount);
        layoutManager.setSpanSizeLookup(new NMatchParentSpanSizeLookup(1, columnCount));

        adapter = new MergeRecyclerAdapter<>();
        adapter.addView(newFeedStartPaddingView());
        adapter.addAdapter(feedAdapter);
        addUserInfoToAdapter(columnCount);

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(null);
        recyclerView.setAdapter(adapter);

        // we can still swipe up if we are not at the start of the feed.
        swipeRefreshLayout.setCanSwipeUpPredicate(() -> !feedAdapter.getFeedProxy().isAtStart());

        swipeRefreshLayout.setOnRefreshListener(() -> {
            FeedProxy proxy = feedAdapter.getFeedProxy();
            if (proxy.isAtStart() && !proxy.isLoading()) {
                proxy.restart(Optional.<Long>absent());
            } else {
                // do not refresh
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        // use height of the toolbar to configure swipe refresh layout.
        int abHeight = AndroidUtility.getActionBarSize(getActivity());
        swipeRefreshLayout.setProgressViewOffset(false, 0, (int) (1.5 * abHeight));
        swipeRefreshLayout.setColorSchemeResources(R.color.primary);

        resetToolbar();

        recyclerView.addOnScrollListener(onScrollListener);
    }

    private void addUserInfoToAdapter(int columnCount) {
        if (userInfoObservable == null) {
            FeedFilter filter = getCurrentFilter();
            if (filter.getUsername().isPresent()) {
                String username = filter.getUsername().get();
                userInfoObservable = userService.info(username)
                        .onErrorResumeNext(Observable.<Info>empty())
                        .cache();

                userInfoViewSupplier = info -> {
                    UserInfoCell view = new UserInfoCell(getActivity(), info);
                    view.setUserActionListener(FeedFragment.this);
                    return view;
                };

            } else if (filter.getTags().isPresent()) {
                String query = filter.getTags().get();
                userInfoObservable = userService.info(query)
                        .onErrorResumeNext(Observable.<Info>empty())
                        .cache();

                userInfoViewSupplier = info -> {
                    UserInfoFoundView view = new UserInfoFoundView(getActivity(), info);
                    view.setUploadsClickedListener((userId, name) -> {
                        FeedFilter newFilter = getCurrentFilter().basic().withUser(name);
                        ((MainActionHandler) getActivity()).onFeedFilterSelected(newFilter);
                    });
                    return view;
                };

            } else {
                userInfoObservable = Observable.empty();
            }
        }

        bindFragment(this, userInfoObservable.limit(1)).subscribe(info -> {
            View userView = userInfoViewSupplier.apply(info);

            // add views to adapter.
            List<View> views = Collections.singletonList(userView);
            adapter.addAdapter(1, new MergeRecyclerAdapter.ViewsAdapter(views));
            layoutManager.setSpanSizeLookup(new NMatchParentSpanSizeLookup(2, columnCount));
        });
    }

    private View newFeedStartPaddingView() {
        int height = AndroidUtility.getActionBarSize(getActivity());

        View view = new View(getActivity());
        view.setLayoutParams(new ViewGroup.LayoutParams(1, height));
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recyclerView.removeOnScrollListener(onScrollListener);
        AndroidUtility.uninjectViews(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Pr0grammApplication.getRefWatcher().watch(this);
    }

    private void removeBusyIndicator() {
        if (busyIndicator != null) {
            ViewParent parent = busyIndicator.getParent();
            ((ViewGroup) parent).removeView(busyIndicator);

            busyIndicator = null;
        }
    }

    private void resetToolbar() {
        if (getActivity() instanceof ToolbarActivity) {
            ToolbarActivity activity = (ToolbarActivity) getActivity();
            activity.getScrollHideToolbarListener().reset();
        }
    }

    private void onBookmarkableStateChanged(boolean bookmarkable) {
        this.bookmarkable = bookmarkable;
        getActivity().supportInvalidateOptionsMenu();
    }

    private FeedAdapter newFeedAdapter() {
        logger.info("Restore adapter now");
        FeedFilter feedFilter = getFilterArgument();

        long startAround = getArguments().getLong(ARG_FEED_START, -1);
        Optional<Long> around = Optional.fromNullable(startAround > 0 ? startAround : null);
        return new FeedAdapter(feedFilter, around);
    }

    private FeedFilter getFilterArgument() {
        return getArguments()
                .<FeedFilter>getParcelable(ARG_FEED_FILTER)
                .withContentType(settings.getContentType());
    }

    @Override
    public void onResume() {
        super.onResume();

        // check if we should show the pin button or not.
        if (settings.showPinButton()) {
            bindFragment(this, bookmarkService.isBookmarkable(getCurrentFilter()))
                    .subscribe(this::onBookmarkableStateChanged, Actions.empty());
        }

        // check if content type has changed, and reload if necessary
        FeedFilter feedFilter = feedAdapter.getFilter();
        EnumSet<ContentType> newContentType = settings.getContentType();
        boolean changed = !equal(feedFilter.getContentTypes(), newContentType);
        if (changed) {
            Optional<Long> around = findFirstVisibleItem(newContentType).transform(FeedItem::getId);

            // set a new adapter if we have a new content type
            FeedFilter filter = feedFilter.withContentType(newContentType);
            feedAdapter = new FeedAdapter(filter, autoScrollOnLoad = around);
            recyclerView.setAdapter(feedAdapter);
        }

        // set new indicator style
        if (seenIndicatorStyle != settings.seenIndicatorStyle()) {
            seenIndicatorStyle = settings.seenIndicatorStyle();
            feedAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Finds the first item in the proxy, that is visible and of one of the given content type.
     *
     * @param contentType The target-content type.
     */
    private Optional<FeedItem> findFirstVisibleItem(Set<ContentType> contentType) {
        List<FeedItem> items = feedAdapter.getFeedProxy().getItems();

        int idx = layoutManager.findFirstVisibleItemPosition();
        if (idx != RecyclerView.NO_POSITION && idx < items.size()) {
            for (FeedItem item : items.subList(idx, items.size() - 1)) {
                if (contentType.contains(item.getContentType())) {
                    return Optional.of(item);
                }
            }
        }

        return Optional.absent();
    }

    /**
     * Depending on whether the screen is landscape or portrait, and how large
     * the screen is, we show a different number of items per row.
     */
    private int getThumbnailColumns() {
        checkNotNull(getActivity(), "must be attached to call this method");

        Configuration config = getResources().getConfiguration();
        boolean portrait = config.screenWidthDp < config.screenHeightDp;

        int screenWidth = config.screenWidthDp;
        return Math.min((int) (screenWidth / 120.0 + 0.5), portrait ? 5 : 7);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_feed, menu);

        MenuItem item = menu.findItem(R.id.action_search);
        initializeSearchView(item);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_refresh);
        if (item != null) {
            item.setVisible(settings.showRefreshButton());
        }

        item = menu.findItem(R.id.action_pin);
        if (item != null) {
            item.setVisible(bookmarkable);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_pin) {
            pinCurrentFeedFilter();
            return true;
        }

        if (item.getItemId() == R.id.action_refresh) {
            // refresh feed
            doRefreshWithIndicator();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void doRefreshWithIndicator() {
        if (swipeRefreshLayout.isRefreshing())
            return;

        swipeRefreshLayout.setRefreshing(true);
        swipeRefreshLayout.postDelayed(() -> {
            resetToolbar();
            feedAdapter.getFeedProxy().restart(Optional.<Long>absent());
        }, 500);
    }

    private void pinCurrentFeedFilter() {
        // not bookmarkable anymore.
        onBookmarkableStateChanged(false);

        FeedFilter filter = getCurrentFilter();
        String title = FeedFilterFormatter.format(getActivity(), filter);
        ((MainActionHandler) getActivity()).pinFeedFilter(filter, title);
    }


    /**
     * Registers the listeners for the search view.
     *
     * @param item The item containing the search view.
     */
    private void initializeSearchView(MenuItem item) {
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(item);

        searchView.setOnSearchClickListener(v -> {
            FeedFilter currentFilter = getCurrentFilter();
            searchView.setQuery(currentFilter.getTags().or(""), false);
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String term) {
                performSearch(term);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String term) {
                return false;
            }
        });
    }

    private void performSearch(String term) {
        FeedFilter current = getCurrentFilter();
        FeedFilter filter = current.withTags(term);

        // do nothing, if the filter did not change
        if (equal(current, filter))
            return;

        ((MainActionHandler) getActivity()).onFeedFilterSelected(filter);
    }

    private void onItemClicked(int idx) {
        try {
            ((MainActionHandler) getActivity()).onPostClicked(feedAdapter.getFeedProxy(), idx);
        } catch (IllegalStateException error) {
            logger.warn("Error while showing post", error);
        }
    }

    /**
     * Creates a new {@link FeedFragment} for the given
     * feed type.
     *
     * @param feedFilter A query to use for getting data
     * @return The type new fragment that can be shown now.
     */
    public static FeedFragment newInstance(FeedFilter feedFilter, Optional<Long> start) {
        Bundle arguments = new Bundle();
        arguments.putParcelable(ARG_FEED_FILTER, feedFilter);
        if (start.isPresent()) {
            arguments.putLong(ARG_FEED_START, start.get());
        }

        FeedFragment fragment = new FeedFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    /**
     * Gets the current filter from this feed.
     *
     * @return The filter this feed uses.
     */
    public FeedFilter getCurrentFilter() {
        if (feedAdapter == null)
            return new FeedFilter();

        return feedAdapter.getFilter();
    }

    @Override
    public void onWriteMessageClicked(int userId, String name) {
        DialogFragment dialog = WritePrivateMessageDialog.newInstance(userId, name);
        dialog.show(getFragmentManager(), null);
    }

    private class FeedAdapter extends RecyclerView.Adapter<FeedItemViewHolder> implements FeedProxy.OnChangeListener {
        private final FeedProxy feedProxy;

        public FeedAdapter(FeedFilter filter, Optional<Long> around) {
            this(new FeedProxy(filter), around);
        }

        public FeedAdapter(FeedProxy feedProxy, Optional<Long> around) {
            this.feedProxy = feedProxy;

            this.feedProxy.setOnChangeListener(this);
            this.feedProxy.setLoader(new FeedProxy.FragmentFeedLoader(FeedFragment.this, feedService) {
                @Override
                public void onLoadFinished() {
                    removeBusyIndicator();
                    swipeRefreshLayout.setRefreshing(false);

                    performAutoOpen();
                    updateNoResultsTextView();
                }

                @Override
                public void onError(Throwable error) {
                    if (getFeedProxy().getItemCount() == 0) {
                        ErrorDialogFragment.showErrorString(
                                getFragmentManager(),
                                getString(R.string.could_not_load_feed));
                    }
                }
            });

            // start the feed
            this.feedProxy.restart(around);
        }

        public FeedFilter getFilter() {
            return feedProxy.getFeedFilter();
        }

        public FeedProxy getFeedProxy() {
            return feedProxy;
        }

        @SuppressLint("InflateParams")
        @Override
        public FeedItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.feed_item_view, null);
            return new FeedItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(FeedItemViewHolder view, int position) {
            FeedItem item = feedProxy.getItemAt(position);

            picasso.load("http://thumb.pr0gramm.com/" + item.getThumb())
                    .into(view.image);

            view.itemView.setOnClickListener(v -> onItemClicked(position));

            // check if this item was already seen.
            if (seenIndicatorStyle == IndicatorStyle.ICON) {
                view.seen.setVisibility(seenService.isSeen(item) ? View.VISIBLE : View.GONE);
            } else {
                view.seen.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return feedProxy.getItemCount();
        }

        @Override
        public long getItemId(int position) {
            return feedProxy.getItemAt(position).getId();
        }

        @Override
        public void onItemRangeInserted(int start, int count) {
            notifyItemRangeInserted(start, count);
        }

        @Override
        public void onItemRangeRemoved(int start, int count) {
            notifyItemRangeRemoved(start, count);
        }
    }

    private void updateNoResultsTextView() {
        boolean empty = feedAdapter.getItemCount() == 0;
        noResultsView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void performAutoOpen() {
        if (autoScrollOnLoad.isPresent()) {
            int idx = findItemIndexById(autoScrollOnLoad.get());
            if (idx >= 0) {
                // over scroll a bit
                int scrollTo = max(idx - 3, 0);
                recyclerView.scrollToPosition(scrollTo);
            }
        }

        if (autoOpenOnLoad.isPresent()) {
            int idx = findItemIndexById(autoOpenOnLoad.get());
            if (idx > 0) {
                onItemClicked(idx);
            }
        }

        autoOpenOnLoad = Optional.absent();
        autoScrollOnLoad = Optional.absent();
    }

    private int findItemIndexById(long id) {
        // look for the index of the item with the given id
        return FluentIterable
                .from(feedAdapter.getFeedProxy().getItems())
                .firstMatch(item -> item.getId() == id)
                .transform(item -> feedAdapter.getFeedProxy().getPosition(item).or(-1))
                .or(-1);
    }

    private RecyclerView.OnScrollListener onScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            if (getActivity() instanceof ToolbarActivity) {
                ToolbarActivity activity = (ToolbarActivity) getActivity();
                activity.getScrollHideToolbarListener().onScrolled(dy);
            }

            int totalItemCount = layoutManager.getItemCount();
            FeedProxy proxy = feedAdapter.getFeedProxy();
            if (proxy.isLoading())
                return;

            if (dy > 0 && !proxy.isAtEnd()) {
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                if (totalItemCount > 12 && lastVisibleItem >= totalItemCount - 12) {
                    logger.info("Request next page now");
                    proxy.loadNextPage();
                }
            }

            if (dy < 0 && !proxy.isAtStart()) {
                int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                if (totalItemCount > 12 && firstVisibleItem < 12) {
                    logger.info("Request previous page now");
                    proxy.loadPreviousPage();
                }
            }
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                if (getActivity() instanceof ToolbarActivity) {
                    int y = estimateRecyclerViewScrollY(recyclerView);

                    ToolbarActivity activity = (ToolbarActivity) getActivity();
                    activity.getScrollHideToolbarListener().onScrollFinished(y);
                }
            }
        }
    };

    private static class FeedItemViewHolder extends RecyclerView.ViewHolder {
        private final ImageView image;
        private final ImageView seen;

        public FeedItemViewHolder(View itemView) {
            super(itemView);

            image = (ImageView) checkNotNull(itemView.findViewById(R.id.image));
            seen = (ImageView) checkNotNull(itemView.findViewById(R.id.seen));
        }
    }
}
