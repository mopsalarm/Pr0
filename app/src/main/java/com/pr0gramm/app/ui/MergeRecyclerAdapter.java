package com.pr0gramm.app.ui;

import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

import com.google.common.base.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Adapted from https://gist.github.com/athornz/008edacd1d3b2f1e1836
 */
public final class MergeRecyclerAdapter extends RecyclerView.Adapter {
    private static final Logger logger = LoggerFactory.getLogger("MergeRecyclerAdapter");

    private final List<Holder> adapters = new ArrayList<>();
    private int globalViewTypeIndex = 0;

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
        if (adapters.size() == 16) {
            throw new IllegalStateException("Can only add up to 16 adapters to merge adapter.");
        }

        if (!adapter.hasStableIds()) {
            logger.warn("Added recycler adapter without stable ids: {}", adapter);
        }

        Holder holder = new Holder(adapter);
        adapters.add(index, holder);

        adapter.registerAdapterDataObserver(holder.observer);
        notifyDataSetChanged();
    }

    public List<? extends RecyclerView.Adapter<?>> getAdapters() {
        ArrayList<RecyclerView.Adapter<?>> copy = new ArrayList<>(adapters.size());
        for (Holder adapter : adapters) {
            copy.add(adapter.adapter);
        }

        return copy;
    }

    @Override
    public int getItemCount() {
        int count = 0;
        for (Holder adapter : adapters)
            count += adapter.adapter.getItemCount();

        return count;
    }

    /**
     * For a given merged position, find the corresponding Adapter and local position within that Adapter by iterating through Adapters and
     * summing their counts until the merged position is found.
     *
     * @param position a merged (global) position
     * @return the matching Adapter and local position, or null if not found
     */
    @Nullable
    private Holder getAdapterOffsetForItem(final int position) {
        final int adapterCount = adapters.size();
        int i = 0;
        int count = 0;

        while (i < adapterCount) {
            Holder a = adapters.get(i);
            int newCount = count + a.adapter.getItemCount();
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
        Holder adapter = checkNotNull(getAdapterOffsetForItem(position),
                "No adapter found for position");

        long uniq = (long) adapter.mAdapterIndex << 60;
        return uniq | (0x0fffffffffffffffL & adapter.adapter.getItemId(adapter.mLocalPosition));
    }

    @Override
    public int getItemViewType(int position) {
        Holder result = checkNotNull(getAdapterOffsetForItem(position),
                "No adapter found for position");

        int localViewType = result.adapter.getItemViewType(result.mLocalPosition);
        int globalViewType = result.viewTypeLocalToGlobal.get(localViewType);
        if (globalViewType != result.viewTypeLocalToGlobal.getNoEntryValue()) {
            return globalViewType;
        }

        // remember new mapping
        globalViewTypeIndex += 1;
        result.viewTypeGlobalToLocal.put(globalViewTypeIndex, localViewType);
        result.viewTypeLocalToGlobal.put(localViewType, globalViewTypeIndex);
        return globalViewTypeIndex;
    }


    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        for (Holder adapter : adapters) {
            int localViewType = adapter.viewTypeGlobalToLocal.get(viewType);
            if (localViewType != adapter.viewTypeGlobalToLocal.getNoEntryValue()) {
                return adapter.adapter.onCreateViewHolder(viewGroup, localViewType);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        Holder result = checkNotNull(getAdapterOffsetForItem(position),
                "No adapter found for position");

        result.adapter.onBindViewHolder(viewHolder, result.mLocalPosition);
    }

    /**
     * Returns the offset of the first item of the given adapter in this one.
     *
     * @param query The adapter to get the offset for.
     */
    public Optional<Integer> getOffset(RecyclerView.Adapter<?> query) {
        int offset = 0;
        for (int idx = 0; idx < adapters.size(); idx++) {
            RecyclerView.Adapter adapter = adapters.get(idx).adapter;
            if (adapter == query)
                return Optional.of(offset);

            offset += adapter.getItemCount();
        }

        return Optional.absent();
    }

    public void clear() {
        for (Holder adapter : this.adapters) {
            adapter.adapter.unregisterAdapterDataObserver(adapter.observer);
        }

        this.adapters.clear();
        this.globalViewTypeIndex = 0;

        notifyDataSetChanged();
    }

    /**
     */
    private class Holder {
        final TIntIntMap viewTypeGlobalToLocal = new TIntIntHashMap(8, 0.75f, -1, -1);
        final TIntIntMap viewTypeLocalToGlobal = new TIntIntHashMap(8, 0.75f, -1, -1);

        final RecyclerView.Adapter adapter;
        final ForwardingDataSetObserver observer;

        int mLocalPosition = 0;
        int mAdapterIndex = 0;

        Holder(RecyclerView.Adapter adapter) {
            this.adapter = adapter;
            this.observer = new ForwardingDataSetObserver(adapter);
        }

        @Override
        public int hashCode() {
            return adapter.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Holder
                    ? adapter.equals(((Holder) o).adapter)
                    : adapter.equals(o);
        }
    }

    private class ForwardingDataSetObserver extends RecyclerView.AdapterDataObserver {
        private final RecyclerView.Adapter owning;

        ForwardingDataSetObserver(RecyclerView.Adapter owning) {
            this.owning = owning;
        }

        @Override
        public void onChanged() {
            notifyDataSetChanged();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            notifyItemRangeChanged(localToMergedIndex(positionStart), itemCount);
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            notifyItemRangeInserted(localToMergedIndex(positionStart), itemCount);
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            notifyItemRangeRemoved(localToMergedIndex(positionStart), itemCount);
        }

        @SuppressWarnings("EqualsBetweenInconvertibleTypes")
        private int localToMergedIndex(int index) {
            int result = index;
            for (Holder adapter : adapters) {
                if (adapter.equals(owning)) {
                    break;
                }

                result += adapter.adapter.getItemCount();
            }
            return result;
        }
    }
}