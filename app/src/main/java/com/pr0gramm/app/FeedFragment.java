package com.pr0gramm.app;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.pr0gramm.app.feed.AbstractFeedAdapter;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.FeedService;
import com.pr0gramm.app.feed.FeedType;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.inject.Inject;

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;

import static com.google.common.base.Preconditions.checkNotNull;
import static rx.android.observables.AndroidObservable.bindFragment;

/**
 */
public class FeedFragment extends RoboFragment {
    @Inject
    private FeedService feedService;

    @InjectView(R.id.list)
    private RecyclerView recyclerView;
    private FeedAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new FeedAdapter();

        // prepare the list of items
        final GridLayoutManager layoutManager = new GridLayoutManager(getActivity(), 3);
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
    }

    private class FeedAdapter extends AbstractFeedAdapter<FeedItemViewHolder> {
        private LayoutInflater inflater = LayoutInflater.from(getActivity());

        FeedAdapter() {
            super(feedService, FeedType.PROMOTED,
                    EnumSet.allOf(ContentType.class), Integer.MAX_VALUE);
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

            Picasso.with(getActivity())
                    .load("http://thumb.pr0gramm.com/" + item.getItem().getThumb())
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
