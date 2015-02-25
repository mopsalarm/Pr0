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

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.pr0gramm.app.feed.AbstractFeedAdapter;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedService;
import com.pr0gramm.app.feed.FeedType;
import com.squareup.picasso.Picasso;

import java.util.EnumSet;
import java.util.Set;

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
        ImmutableSet<ContentType> contentTypes = FluentIterable
                .from(sharedPreferences.getStringSet(
                        "FeedFragment.contentType",
                        singleton(ContentType.SFW.toString())))
                .transform(ContentType::valueOf)
                .toSet();

        FeedType feedType = FeedType.valueOf(sharedPreferences.getString(
                "FeedFragment.feedType", FeedType.PROMOTED.toString()));

        return new FeedAdapter(feedType, contentTypes);
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
        item.setTitle(ContentType.toString(getActivity(), adapter.getContentTypes()));
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
        FeedAdapter adapter = new FeedAdapter(this.adapter.getFeedType(), contentTypes);

        setNewFeedAdapter(adapter);
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

    private void storeAdapterConfiguration() {
        Log.i("FeedFragment", "storing adapter configuration");

        sharedPreferences.edit()
                .putString("FeedFragment.feedType", adapter.getFeedType().toString())
                .putStringSet("FeedFragment.contentType", FluentIterable
                        .from(adapter.getContentTypes())
                        .transform(ContentType::toString)
                        .toSet())
                .apply();
    }

    private class FeedAdapter extends AbstractFeedAdapter<FeedItemViewHolder> {
        private LayoutInflater inflater = LayoutInflater.from(getActivity());

        FeedAdapter(FeedType type, Set<ContentType> contentTypes) {
            super(feedService, type, contentTypes, Optional.<FeedItem>absent());
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
