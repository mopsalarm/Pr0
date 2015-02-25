package com.pr0gramm.app;

import android.annotation.SuppressLint;
import android.graphics.Point;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

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

    private FeedAdapter adapter;
    private GridLayoutManager layoutManager;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new FeedAdapter(EnumSet.of(ContentType.SFW));

        // prepare the list of items
        int count = getThumbnailColumns();
        layoutManager = new GridLayoutManager(getActivity(), count);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        // load next page if we are near the end of the list.
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

        //  tell the activity that we provide a menu
        setHasOptionsMenu(true);
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
        adapter = new FeedAdapter(contentTypes);
        recyclerView.setAdapter(adapter);

        getActivity().supportInvalidateOptionsMenu();
    }

    private class FeedAdapter extends AbstractFeedAdapter<FeedItemViewHolder> {
        private LayoutInflater inflater = LayoutInflater.from(getActivity());

        FeedAdapter(Set<ContentType> contentTypes) {
            super(feedService, FeedType.PROMOTED, contentTypes, Integer.MAX_VALUE);
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
