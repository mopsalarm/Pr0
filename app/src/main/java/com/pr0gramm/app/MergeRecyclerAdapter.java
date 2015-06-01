package com.pr0gramm.app;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rx.functions.Action1;

/**
 * Adapted from https://gist.github.com/athornz/008edacd1d3b2f1e1836
 */
public class MergeRecyclerAdapter extends RecyclerView.Adapter {

    private final List<LocalAdapter> adapters = new ArrayList<>();
    private int mViewTypeIndex = 0;

    /**
     */
    public class LocalAdapter {
        public final RecyclerView.Adapter mAdapter;
        public int mLocalPosition = 0;
        public int mAdapterIndex = 0;
        public Map<Integer, Integer> mViewTypesMap = new HashMap<>();

        public LocalAdapter(RecyclerView.Adapter adapter) {
            mAdapter = adapter;
        }

        @Override
        public int hashCode() {
            return mAdapter.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof MergeRecyclerAdapter.LocalAdapter
                    ? mAdapter.equals(((MergeRecyclerAdapter.LocalAdapter) o).mAdapter)
                    : mAdapter.equals(o);
        }
    }

    public MergeRecyclerAdapter() {
        setHasStableIds(true);
    }

    /**
     * Append the given adapter to the list of merged adapters.
     */
    public void addAdapter(RecyclerView.Adapter adapter) {
        addAdapter(adapters.size(), adapter);
    }

    /**
     * Add the given adapter to the list of merged adapters at the given index.
     */
    public void addAdapter(int index, RecyclerView.Adapter adapter) {
        adapters.add(index, new LocalAdapter(adapter));
        adapter.registerAdapterDataObserver(new ForwardingDataSetObserver(this, adapter));
        notifyDataSetChanged();
    }

    /**
     * Adds a new View to the roster of things to appear in
     * the aggregate list.
     *
     * @param view Single view to add
     */
    public void addView(View view) {
        addViews(Collections.singletonList(view));
    }

    /**
     * Adds a list of views to the roster of things to appear
     * in the aggregate list.
     *
     * @param views List of views to add
     */
    @SuppressWarnings("unchecked")
    public void addViews(List<View> views) {
        addAdapter(new ViewsAdapter(views));
    }

    @Override
    public int getItemCount() {
        int count = 0;
        for (LocalAdapter adapter : adapters)
            count += adapter.mAdapter.getItemCount();

        return count;
    }

    /**
     * For a given merged position, find the corresponding Adapter and local position within that Adapter by iterating through Adapters and
     * summing their counts until the merged position is found.
     *
     * @param position a merged (global) position
     * @return the matching Adapter and local position, or null if not found
     */
    public LocalAdapter getAdapterOffsetForItem(final int position) {
        final int adapterCount = adapters.size();
        int i = 0;
        int count = 0;

        while (i < adapterCount) {
            LocalAdapter a = adapters.get(i);
            int newCount = count + a.mAdapter.getItemCount();
            if (position < newCount) {
                a.mLocalPosition = position - count;
                a.mAdapterIndex = i;
                return a;
            }
            count = newCount;
            i++;
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        LocalAdapter adapter = getAdapterOffsetForItem(position);

        long uniq = (long) adapter.mAdapterIndex << 60;
        return uniq | (0x0fffffffffffffffL & adapter.mAdapter.getItemId(adapter.mLocalPosition));
    }


    @Override
    public int getItemViewType(int position) {
        LocalAdapter result = getAdapterOffsetForItem(position);
        int localViewType = result.mAdapter.getItemViewType(result.mLocalPosition);
        if (result.mViewTypesMap.containsValue(localViewType)) {
            for (Map.Entry<Integer, Integer> entry : result.mViewTypesMap.entrySet()) {
                if (entry.getValue() == localViewType) {
                    return entry.getKey();
                }
            }
        }
        mViewTypeIndex += 1;
        result.mViewTypesMap.put(mViewTypeIndex, localViewType);
        return mViewTypeIndex;
    }


    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        for (LocalAdapter adapter : adapters) {
            if (adapter.mViewTypesMap.containsKey(viewType))
                return adapter.mAdapter.onCreateViewHolder(viewGroup, adapter.mViewTypesMap.get(viewType));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        LocalAdapter result = getAdapterOffsetForItem(position);
        result.mAdapter.onBindViewHolder(viewHolder, result.mLocalPosition);
    }

    private static class ForwardingDataSetObserver extends RecyclerView.AdapterDataObserver {
        private final RecyclerView.Adapter owning;
        private final WeakReference<MergeRecyclerAdapter> merge;

        ForwardingDataSetObserver(MergeRecyclerAdapter merge, RecyclerView.Adapter owning) {
            this.owning = owning;
            this.merge = new WeakReference<>(merge);
        }

        @Override
        public void onChanged() {
            withAdapter(MergeRecyclerAdapter::notifyDataSetChanged);
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            withAdapter(base -> base.notifyItemRangeChanged(
                    localToMergedIndex(base, positionStart), itemCount));
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            withAdapter(base -> base.notifyItemRangeInserted(
                    localToMergedIndex(base, positionStart), itemCount));
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            withAdapter(base -> base.notifyItemRangeRemoved(
                    localToMergedIndex(base, positionStart), itemCount));
        }

        @SuppressWarnings("EqualsBetweenInconvertibleTypes")
        private int localToMergedIndex(MergeRecyclerAdapter base, int index) {
            int result = index;
            for (LocalAdapter adapter : base.adapters) {
                if (adapter.equals(owning)) {
                    break;
                }

                result += adapter.mAdapter.getItemCount();
            }
            return result;
        }

        private void withAdapter(Action1<MergeRecyclerAdapter> action) {
            MergeRecyclerAdapter adapter = merge.get();
            if (adapter != null) {
                action.call(adapter);
            }
        }
    }


    /**
     * ViewsAdapter, ported from CommonsWare SackOfViews adapter.
     */
    public static class ViewsAdapter extends RecyclerView.Adapter {
        private List<View> views = null;

        /**
         * Constructor wrapping a supplied list of views.
         * Subclasses must override newView() if any of the elements
         * in the list are null.
         */
        public ViewsAdapter(List<View> views) {
            super();
            this.views = views;
        }

        /**
         * How many items are in the data set represented by this
         * Adapter.
         */
        @Override
        public int getItemCount() {
            return views.size();
        }

        /**
         * Get the type of View that will be created by getView()
         * for the specified item.
         *
         * @param position Position of the item whose data we want
         */
        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            //view type is equal to the position in this adapter.
            return new ViewsViewHolder(views.get(viewType));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        }

        /**
         * Get the row id associated with the specified position
         * in the list.
         *
         * @param position Position of the item whose data we want
         */
        @Override
        public long getItemId(int position) {
            return position;
        }
    }

    public static class ViewsViewHolder extends RecyclerView.ViewHolder {
        public ViewsViewHolder(View itemView) {
            super(itemView);
        }
    }
}