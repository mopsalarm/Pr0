package com.pr0gramm.app.ui

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import com.google.common.base.Preconditions.checkNotNull
import gnu.trove.map.TIntIntMap
import gnu.trove.map.hash.TIntIntHashMap
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Adapted from https://gist.github.com/athornz/008edacd1d3b2f1e1836
 */
class MergeRecyclerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val adapters = ArrayList<Holder>()
    private var globalViewTypeIndex = 0

    init {
        setHasStableIds(true)
    }

    /**
     * Append the given adapter to the list of merged adapters.
     */
    fun addAdapter(adapter: RecyclerView.Adapter<out RecyclerView.ViewHolder>) {
        addAdapter(adapters.size, adapter)
    }

    /**
     * Add the given adapter to the list of merged adapters at the given index.
     */
    fun addAdapter(index: Int, adapter: RecyclerView.Adapter<out RecyclerView.ViewHolder>) {
        if (adapters.size == 16) {
            throw IllegalStateException("Can only add up to 16 adapters to merge adapter.")
        }

        if (!adapter.hasStableIds()) {
            logger.warn("Added recycler adapter without stable ids: {}", adapter)
        }

        @Suppress("UNCHECKED_CAST")
        val holder = Holder(adapter as RecyclerView.Adapter<RecyclerView.ViewHolder>)
        adapters.add(index, holder)

        adapter.registerAdapterDataObserver(holder.observer)
        notifyDataSetChanged()
    }

    fun getAdapters(): List<RecyclerView.Adapter<out RecyclerView.ViewHolder>> {
        return adapters.map { it.adapter }
    }

    override fun getItemCount(): Int {
        return adapters.sumBy { it.adapter.itemCount }
    }

    /**
     * For a given merged position, find the corresponding Adapter and local position within that Adapter by iterating through Adapters and
     * summing their counts until the merged position is found.

     * @param position a merged (global) position
     * *
     * @return the matching Adapter and local position, or null if not found
     */
    private fun getAdapterOffsetForItem(position: Int): Holder? {
        val adapterCount = adapters.size
        var i = 0
        var count = 0

        while (i < adapterCount) {
            val a = adapters[i]
            val newCount = count + a.adapter.itemCount
            if (position < newCount) {
                a.mLocalPosition = position - count
                a.mAdapterIndex = i
                return a
            }
            count = newCount
            i++
        }
        return null
    }

    override fun getItemId(position: Int): Long {
        val adapter = checkNotNull<Holder>(getAdapterOffsetForItem(position), "No adapter found for position")

        val uniq = adapter.mAdapterIndex.toLong() shl 60
        return uniq or (0x0fffffffffffffffL and adapter.adapter.getItemId(adapter.mLocalPosition))
    }

    override fun getItemViewType(position: Int): Int {
        val result = checkNotNull<Holder>(getAdapterOffsetForItem(position), "No adapter found for position")

        val localViewType = result.adapter.getItemViewType(result.mLocalPosition)
        val globalViewType = result.viewTypeLocalToGlobal.get(localViewType)
        if (globalViewType != result.viewTypeLocalToGlobal.noEntryValue) {
            return globalViewType
        }

        // remember new mapping
        globalViewTypeIndex += 1
        result.viewTypeGlobalToLocal.put(globalViewTypeIndex, localViewType)
        result.viewTypeLocalToGlobal.put(localViewType, globalViewTypeIndex)
        return globalViewTypeIndex
    }


    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RecyclerView.ViewHolder? {
        for (adapter in adapters) {
            val localViewType = adapter.viewTypeGlobalToLocal.get(viewType)
            if (localViewType != adapter.viewTypeGlobalToLocal.noEntryValue) {
                return adapter.adapter.onCreateViewHolder(viewGroup, localViewType)
            }
        }
        return null
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        val result = getAdapterOffsetForItem(position)!!
        result.adapter.onBindViewHolder(viewHolder, result.mLocalPosition)
    }

    /**
     * Returns the offset of the first item of the given adapter in this one.

     * @param query The adapter to get the offset for.
     */
    fun getOffset(query: RecyclerView.Adapter<*>): Int? {
        var offset = 0
        for (idx in adapters.indices) {
            val adapter = adapters[idx].adapter
            if (adapter === query)
                return offset

            offset += adapter.itemCount
        }

        return null
    }

    fun clear() {
        for (adapter in this.adapters) {
            adapter.adapter.unregisterAdapterDataObserver(adapter.observer)
        }

        this.adapters.clear()
        this.globalViewTypeIndex = 0

        notifyDataSetChanged()
    }

    /**
     */
    private inner class Holder(val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>) {
        val viewTypeGlobalToLocal: TIntIntMap = TIntIntHashMap(8, 0.75f, -1, -1)
        val viewTypeLocalToGlobal: TIntIntMap = TIntIntHashMap(8, 0.75f, -1, -1)
        val observer: ForwardingDataSetObserver = ForwardingDataSetObserver(adapter)

        var mLocalPosition = 0
        var mAdapterIndex = 0
    }

    private inner class ForwardingDataSetObserver(
            val owning: RecyclerView.Adapter<out RecyclerView.ViewHolder>) : RecyclerView.AdapterDataObserver() {

        override fun onChanged() {
            notifyDataSetChanged()
        }

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
            notifyItemRangeChanged(localToMergedIndex(positionStart), itemCount)
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            notifyItemRangeInserted(localToMergedIndex(positionStart), itemCount)
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            notifyItemRangeRemoved(localToMergedIndex(positionStart), itemCount)
        }

        private fun localToMergedIndex(index: Int): Int {
            var result = index
            for (adapter in adapters) {
                if (adapter.adapter === owning) {
                    break
                }

                result += adapter.adapter.itemCount
            }
            return result
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("MergeRecyclerAdapter")
    }
}