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

import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.inject.Inject;

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;
import rx.Observable;
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
        recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 3));
        recyclerView.setAdapter(adapter);

        // start loading the first page
        Observable<List<FeedItem>> query = feedService.getFeed(
                FeedService.FeedType.PROMOTED,
                EnumSet.of(FeedService.ContentType.SFW));

        bindFragment(this, query).subscribe(new Action1<List<FeedItem>>() {
            @Override
            public void call(List<FeedItem> feedItems) {
                adapter.append(feedItems);
            }
        });

    }

    private class FeedAdapter extends RecyclerView.Adapter<FeedItemViewHolder> {
        private List<FeedItem> items = new ArrayList<>();
        private LayoutInflater inflater = LayoutInflater.from(getActivity());

        private FeedAdapter() {
            setHasStableIds(true);
        }

        @Override
        public FeedItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            @SuppressLint("InflateParams") View view = inflater.inflate(R.layout.feed_item_view, null);
            return new FeedItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(FeedItemViewHolder view, int position) {
            FeedItem item = items.get(position);

            Picasso.with(getActivity())
                    .load("http://thumb.pr0gramm.com/" + item.getItem().getThumb())
                    .into(view.image);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).getId();
        }

        /**
         * Appends the new items to the items that are currently stored
         * in this adapter instance.
         *
         * @param newItems The new items to append to the adapter.
         */
        public void append(List<FeedItem> newItems) {
            int oldCount = items.size();
            items.addAll(newItems);
            notifyItemRangeInserted(oldCount, newItems.size());
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
