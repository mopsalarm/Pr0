package com.pr0gramm.app.ui.fragments;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.google.common.base.Optional;
import com.pr0gramm.app.AndroidUtility;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedProxy;
import com.pr0gramm.app.feed.FeedService;
import com.pr0gramm.app.feed.Query;
import com.pr0gramm.app.services.SeenService;
import com.pr0gramm.app.ui.MainActivity;
import com.pr0gramm.app.ui.views.CustomSwipeRefreshLayout;
import com.squareup.picasso.Picasso;

import javax.inject.Inject;

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity;

/**
 */
public class FeedFragment extends RoboFragment {
    private static final String ARG_FEED_QUERY = "FeedFragment.query";

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
    private View progressView;

    @InjectView(R.id.refresh)
    private CustomSwipeRefreshLayout swipeRefreshLayout;

    private FeedAdapter adapter;
    private GridLayoutManager layoutManager;
    private IndicatorStyle seenIndicatorStyle;


    /**
     * Initialize a new feed fragment.
     */
    public FeedFragment() {
        setHasOptionsMenu(true);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (adapter == null) {
            // create a new adapter if necessary
            adapter = newFeedAdapter();
            progressView.setVisibility(View.VISIBLE);
        }

        seenIndicatorStyle = settings.seenIndicatorStyle();

        // prepare the list of items
        int count = getThumbnailColumns();
        layoutManager = new GridLayoutManager(getActivity(), count);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        // we can still swipe up if we are not at the start of the feed.
        swipeRefreshLayout.setCanSwipeUpPredicate(() -> !adapter.getFeedProxy().isAtStart());

        swipeRefreshLayout.setOnRefreshListener(() -> {
            FeedProxy proxy = adapter.getFeedProxy();
            if (proxy.isAtStart() && !proxy.isLoading()) {
                proxy.restart();
            } else {
                // do not refresh
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        // use height of the toolbar to configure swipe refresh layout.
        int abHeight = AndroidUtility.getActionBarSize(getActivity());
        swipeRefreshLayout.setProgressViewOffset(false, 0, (int) (1.5 * abHeight));
        swipeRefreshLayout.setColorSchemeResources(R.color.primary);

        if (getActivity() instanceof ToolbarActivity) {
            ToolbarActivity activity = (ToolbarActivity) getActivity();
            activity.getScrollHideToolbarListener().reset();
        }

        setupInfiniteScroll();
    }

    private FeedAdapter newFeedAdapter() {
        Log.i("Feed", "Restore adapter now");
        Query query = getArguments()
                .<Query>getParcelable(ARG_FEED_QUERY)
                .withContentType(settings.getContentType());

        return new FeedAdapter(query);
    }

    @Override
    public void onResume() {
        super.onResume();

        // check if content type has changed, and reload if necessary
        Query query = adapter.getQuery();
        boolean changed = !equal(query.getContentTypes(), settings.getContentType());
        if (changed) {
            Query newQuery = query.withContentType(settings.getContentType());
            setNewQuery(newQuery);
        }

        // set new indicator style
        if (seenIndicatorStyle != settings.seenIndicatorStyle()) {
            seenIndicatorStyle = settings.seenIndicatorStyle();
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * Loads the next page when we are near the end of one page.
     */
    private void setupInfiniteScroll() {
        recyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (getActivity() instanceof ToolbarActivity) {
                    ToolbarActivity activity = (ToolbarActivity) getActivity();
                    activity.getScrollHideToolbarListener().onScrolled(dy);
                }

                int totalItemCount = layoutManager.getItemCount();
                FeedProxy proxy = adapter.getFeedProxy();
                if (proxy.isLoading())
                    return;

                if (dy > 0 && !proxy.isAtEnd()) {
                    int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                    if (totalItemCount > 12 && lastVisibleItem >= totalItemCount - 12) {
                        Log.i("FeedScroll", "Request next page now");
                        proxy.loadNextPage();
                    }
                }

                if (dy < 0 && !proxy.isAtStart()) {
                    int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                    if (totalItemCount > 12 && firstVisibleItem < 12) {
                        Log.i("FeedScroll", "Request previous page now");
                        proxy.loadPreviousPage();
                    }
                }
            }
        });
    }

    /**
     * Depending on whether the screen is landscape or portrait,
     * we show a different number of items per row.
     */
    private int getThumbnailColumns() {
        checkNotNull(getActivity(), "must be attached to call this method");

        Display display = getActivity().getWindowManager().getDefaultDisplay();

        Point point = new Point();
        display.getSize(point);

        return point.x > point.y ? 5 : 3;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_feed, menu);

        MenuItem item = menu.findItem(R.id.action_search);
        initializeSearchView(item);
    }

    /**
     * Registers the listeners for the search view.
     *
     * @param item The item containing the search view.
     */
    private void initializeSearchView(MenuItem item) {
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(item);

        // set the search value from the query.
        Optional<String> term = adapter.getQuery().getTags();
        if (term.isPresent()) {
            item.expandActionView();
            searchView.setQuery(term.get(), false);
            searchView.clearFocus();
        }

        // TODO hide searchView completely if we have a term and only
        // TODO use that term for the query.

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String term) {
                setSearchTerm(term);
                searchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String term) {
                return false;
            }
        });

        MenuItemCompat.setOnActionExpandListener(item, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                if (term.isPresent()) {
                    getFragmentManager().popBackStack();
                    return true;
                }

                clearSearchTerm();
                return true;
            }
        });
    }

    private void setSearchTerm(String term) {
        Query query = adapter.getQuery();
        if (term.equalsIgnoreCase(query.getTags().orNull()))
            return;

        Query newQuery = query.withTags(term);
        setNewQuery(newQuery);
    }

    private void clearSearchTerm() {
        Query query = adapter.getQuery();
        if (query.getTags().isPresent())
            setNewQuery(query.withNoTags());
    }

    private void setNewQuery(Query newQuery) {
        // set and store adapter
        this.adapter = new FeedAdapter(newQuery);
        recyclerView.setAdapter(adapter);
    }

    private void onItemClicked(FeedItem item, int idx) {
        ((MainActivity) getActivity()).onPostClicked(adapter.getFeedProxy(), idx);
    }

    /**
     * Creates a new {@link FeedFragment} for the given
     * feed type.
     *
     * @param query A query to use for getting data
     * @return The type new fragment that can be shown now.
     */
    public static FeedFragment newInstance(Query query) {
        Bundle arguments = new Bundle();
        arguments.putParcelable(ARG_FEED_QUERY, query);

        FeedFragment fragment = new FeedFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    private class FeedAdapter extends RecyclerView.Adapter<FeedItemViewHolder> implements FeedProxy.OnChangeListener {
        private final FeedProxy feedProxy;

        public FeedAdapter(Query query) {
            this(new FeedProxy(query));
        }

        public FeedAdapter(FeedProxy feedProxy) {
            this.feedProxy = feedProxy;

            this.feedProxy.setOnChangeListener(this);
            this.feedProxy.setLoader(new FeedProxy.FragmentFeedLoader(FeedFragment.this, feedService) {
                @Override
                public void onLoadFinished() {
                    progressView.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                }
            });

            // start the feed
            this.feedProxy.restart();
        }

        public Query getQuery() {
            return feedProxy.getQuery();
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

            view.itemView.setOnClickListener(v -> onItemClicked(item, position));

            int row = position / layoutManager.getSpanCount();
            view.itemView.setPadding(0, row == 0 ? AndroidUtility.getActionBarSize(getActivity()) : 0, 0, 0);

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
