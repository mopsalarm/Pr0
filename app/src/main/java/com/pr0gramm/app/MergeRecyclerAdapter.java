package com.pr0gramm.app;

import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
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
        public final Map<Integer, Integer> mViewTypesMap = new HashMap<>();
        public final RecyclerView.Adapter mAdapter;
        public int mLocalPosition = 0;
        public int mAdapterIndex = 0;

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

    public ImmutableList<? extends RecyclerView.Adapter<?>> getAdapters() {
        return FluentIterable.from(adapters)
                .transform(a -> (RecyclerView.Adapter<?>) a.mAdapter)
                .toList();
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

    /**
     * Returns the offset of the first item of the given adapter in this one.
     *
     * @param query The adapter to get the offset for.
     */
    public Optional<Integer> getOffset(RecyclerView.Adapter<?> query) {
        int offset = 0;
        for (int idx = 0; idx < adapters.size(); idx++) {
            RecyclerView.Adapter adapter = adapters.get(idx).mAdapter;
            if (adapter == query)
                return Optional.of(offset);

            offset += adapter.getItemCount();
        }

        return Optional.absent();
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
}