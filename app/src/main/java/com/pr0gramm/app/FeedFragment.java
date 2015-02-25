package com.pr0gramm.app;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.pr0gramm.app.feed.AbstractFeedAdapter;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedService;
import com.pr0gramm.app.feed.FeedType;
import com.squareup.picasso.Picasso;

import java.util.EnumSet;

import javax.inject.Inject;

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;
import rx.Observable;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.singleton;
import static rx.android.observables.AndroidObservable.bindFragment;

/**
 */
public class FeedFragment extends RoboFragment implements ChangeContentTypeDialog.ContentTypeChangeListener {
    public static final String PREF_CONTENT_TYPE = "FeedFragment.contentType";
    public static final String PREF_FEED_TYPE = "FeedFragment.feedType";

    @Inject
    private FeedService feedService;

    @Inject
    private Picasso picasso;

    @InjectView(R.id.list)
    private RecyclerView recyclerView;

    @Inject
    private SharedPreferences sharedPreferences;

    private FeedAdapter adapter;

    private GridLayoutManager layoutManager;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = restoreFeedAdapter();

        // prepare the list of items
        int count = getThumbnailColumns();
        layoutManager = new GridLayoutManager(getActivity(), count);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        setupInfiniteScroll();

        //  tell the activity that we provide a menu
        setHasOptionsMenu(true);
    }

    private FeedAdapter restoreFeedAdapter() {
        FeedService.Query query = new FeedService.Query();

        try {
            ImmutableSet<ContentType> contentTypes = FluentIterable
                    .from(sharedPreferences.getStringSet(PREF_CONTENT_TYPE, singleton(ContentType.SFW.toString())))
                    .transform(ContentType::valueOf)
                    .toSet();

            // update query
            query = query.withContentType(contentTypes);

        } catch (Exception ignored) {
            // could not deserialize value
        }

        try {
            FeedType feedType = FeedType.valueOf(sharedPreferences.getString(
                    PREF_FEED_TYPE, FeedType.PROMOTED.toString()));

            // update query
            query = query.withFeedType(feedType);

        } catch (Exception ignored) {
            // could not deserialize value
        }

        return new FeedAdapter(query);
    }

    /**
     * Loads the next page when we are near the end of one page.
     */
    private void setupInfiniteScroll() {
        recyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int totalItemCount = layoutManager.getItemCount();
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();

                if (totalItemCount > 12 && lastVisibleItem >= totalItemCount - 12) {
                    adapter.loadNextPage();
                }
            }
        });
    }

    /**
     * Depending on whether the screen is landscape or portrait,
     * we show a different number of items per row.
     */
    private int getThumbnailColumns() {
        Display display = getActivity().getWindowManager().getDefaultDisplay();

        Point point = new Point();
        display.getSize(point);

        return point.x > point.y ? 5 : 3;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_feed, menu);

        MenuItem item = menu.findItem(R.id.action_change_content_type);
        item.setTitle(ContentType.toString(getActivity(), adapter.getQuery().getContentTypes()));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_change_content_type) {
            showChangeContentTypesDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showChangeContentTypesDialog() {
        new ChangeContentTypeDialog().show(getChildFragmentManager(), null);
    }

    @Override
    public void onContentTypeChanged(EnumSet<ContentType> contentTypes) {
        FeedService.Query newQuery = this.adapter.getQuery().withContentType(contentTypes);
        setNewFeedAdapter(new FeedAdapter(newQuery));
    }

    private void setNewFeedAdapter(FeedAdapter newAdapter) {
        // set and store adapter
        this.adapter = newAdapter;
        recyclerView.setAdapter(adapter);

        // remember settings
        storeAdapterConfiguration();

        // we must update menu items
        getActivity().supportInvalidateOptionsMenu();
    }

    /**
     * Stores the current config of the feed to view it later again.
     */
    private void storeAdapterConfiguration() {
        Log.i("FeedFragment", "storing adapter configuration");

        FeedService.Query query = adapter.getQuery();
        sharedPreferences.edit()
                .putString(PREF_FEED_TYPE, query.getFeedType().toString())
                .putStringSet(PREF_CONTENT_TYPE, FluentIterable
                        .from(query.getContentTypes())
                        .transform(ContentType::toString)
                        .toSet())
                .apply();
    }

    private class FeedAdapter extends AbstractFeedAdapter<FeedItemViewHolder> {
        private LayoutInflater inflater = LayoutInflater.from(getActivity());

        FeedAdapter(FeedService.Query query) {
            super(feedService, query);
        }

        @SuppressLint("InflateParams")
        @Override
        public FeedItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = inflater.inflate(R.layout.feed_item_view, null);
            return new FeedItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(FeedItemViewHolder view, int position) {
            FeedItem item = getItem(position);

            picasso.load("http://thumb.pr0gramm.com/" + item.getItem().getThumb())
                    .into(view.image);
        }

        @Override
        protected <E> Observable<E> bind(Observable<E> observable) {
            return bindFragment(FeedFragment.this, observable);
        }
    }

    private static class FeedItemViewHolder extends RecyclerView.ViewHolder {
        private final ImageView image;

        public FeedItemViewHolder(View itemView) {
            super(itemView);

            image = (ImageView) checkNotNull(itemView.findViewById(R.id.image));
        }
    }
}
