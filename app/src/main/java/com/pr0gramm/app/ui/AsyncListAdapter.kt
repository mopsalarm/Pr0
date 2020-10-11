package com.pr0gramm.app.ui

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.ui.base.Main
import com.pr0gramm.app.util.checkMainThread
import com.pr0gramm.app.util.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class AsyncListAdapter<T : Any, V : RecyclerView.ViewHolder>(
        private val diffCallback: DiffUtil.ItemCallback<T> = InstanceDiffCallback(),
        private val detectMoves: Boolean = false) : RecyclerView.Adapter<V>() {

    // updates also go to the state
    private val mutableState = MutableStateFlow<List<T>>(listOf())

    // Max generation of currently scheduled runnable
    private var maxScheduledGeneration: Int = 0

    var updating: Boolean = false
        private set

    val state: Flow<List<T>>
        get() = mutableState

    var items: List<T> = listOf()
        private set

    override fun getItemCount(): Int {
        return items.size
    }

    fun getItem(idx: Int): T = items[idx]

    /**
     * Pass a new List to the AdapterHelper. Adapter updates will be computed on a background
     * thread.
     *
     *
     * If a List is already present, a diff will be computed asynchronously on a background thread.
     * When the diff is computed, it will be applied (dispatched to the [ListUpdateCallback]),
     * and the new List will be swapped in.
     *
     * @param newList The new List.
     */
    open fun submitList(newList: List<T>, forceSync: Boolean = false, callback: Callback? = null) {
        checkMainThread()

        val oldList = items

        if (newList === oldList) {
            // nothing to do
            return
        }

        trace { "submitList(old=${oldList.size}, new=${newList.size})" }

        // incrementing generation means any currently-running diffs are discarded when they finish
        val runGeneration = ++maxScheduledGeneration

        updating = true

        // fast simple remove all
        if (newList.isEmpty()) {
            applyNewItems(emptyList()) {
                notifyItemRangeRemoved(0, oldList.size)
            }

            return
        }

        // fast simple insert
        if (oldList.isEmpty()) {
            applyNewItems(newList) {
                notifyItemRangeInserted(0, newList.size)
            }

            return
        }

        if (forceSync || oldList.size < 32 || newList.size < 32) {
            applyNewItems(newList) {
                calculateDiff(oldList, newList).dispatchUpdatesTo(this)
            }

            callback?.invoke()

        } else {
            CoroutineScope(Main).launch {
                val diff = withContext(Dispatchers.Default) {
                    calculateDiff(oldList, newList)
                }

                if (maxScheduledGeneration == runGeneration) {
                    applyNewItems(newList) {
                        diff.dispatchUpdatesTo(this@AsyncListAdapter)
                    }

                    callback?.invoke()
                }
            }
        }
    }

    private inline fun applyNewItems(items: List<T>, dispatchToAdapter: () -> Unit) {
        trace { "applyNewItems(${items.size} items)" }

        this.items = items
        this.updating = false

        // dispatch changes to listeners. We inform the native recyclerview listeners before
        // we inform any other listeners using the state flow below.
        dispatchToAdapter()

        mutableState.value = items
    }

    private fun calculateDiff(oldList: List<T>, newList: List<T>): DiffUtil.DiffResult {
        return DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int {
                return oldList.size
            }

            override fun getNewListSize(): Int {
                return newList.size
            }

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return diffCallback.areItemsTheSame(
                        oldList[oldItemPosition], newList[newItemPosition])
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return diffCallback.areContentsTheSame(
                        oldList[oldItemPosition], newList[newItemPosition])
            }

            override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
                return diffCallback.getChangePayload(
                        oldList[oldItemPosition], newList[newItemPosition])
            }
        }, detectMoves)
    }

    class InstanceDiffCallback<T> : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
            return oldItem === newItem
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
            return oldItem == newItem
        }
    }

    class KeyDiffCallback<T>(private val keyOf: (T) -> Any?) : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
            return keyOf(oldItem) == keyOf(newItem)
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
            return oldItem == newItem
        }
    }
}
