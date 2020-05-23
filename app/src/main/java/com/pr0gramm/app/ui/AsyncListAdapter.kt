package com.pr0gramm.app.ui

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import com.pr0gramm.app.Logger
import com.pr0gramm.app.ui.base.Main
import com.pr0gramm.app.ui.base.withBackgroundContext
import com.pr0gramm.app.util.checkMainThread
import com.pr0gramm.app.util.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import rx.Observable
import rx.subjects.PublishSubject

abstract class AsyncListAdapter<T : Any, V : androidx.recyclerview.widget.RecyclerView.ViewHolder>(
        private val diffCallback: DiffUtil.ItemCallback<T> = InstanceDiffCallback(),
        private val detectMoves: Boolean = false,
        name: String = "AsyncListAdapter") : androidx.recyclerview.widget.RecyclerView.Adapter<V>() {

    internal val logger = Logger(name)

    private val updateSubject: PublishSubject<List<T>> = PublishSubject.create()
    val updates = updateSubject as Observable<List<T>>

    var items: List<T> = listOf()
        private set

    // Max generation of currently scheduled runnable
    private var maxScheduledGeneration: Int = 0

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
    open fun submitList(newList: List<T>, forceSync: Boolean = false) {
        checkMainThread()

        val oldList = items

        if (newList === oldList) {
            // nothing to do
            return
        }

        trace { "submitList(new=${newList.size} items, old=${oldList.size})" }

        // incrementing generation means any currently-running diffs are discarded when they finish
        val runGeneration = ++maxScheduledGeneration

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

        if (!forceSync && (oldList.size > 32 || newList.size > 32)) {
            CoroutineScope(Main).launch {
                val diff = withBackgroundContext {
                    logger.debug { "Calculate diff in background" }
                    calculateDiff(oldList, newList)
                }

                if (maxScheduledGeneration == runGeneration) {
                    applyNewItems(newList) {
                        diff.dispatchUpdatesTo(this@AsyncListAdapter)
                    }
                }
            }
        } else {
            val diff = calculateDiff(oldList, newList)
            applyNewItems(newList) {
                diff.dispatchUpdatesTo(this)
            }
        }
    }

    private inline fun applyNewItems(items: List<T>, dispatch: () -> Unit) {
        trace { "applyNewItems(${items.size} items)" }

        checkMainThread()

        preApplyNewItems(items)

        this.items = items
        dispatch()
        updateSubject.onNext(items)
    }

    protected open fun preApplyNewItems(items: List<T>) {}

    private fun calculateDiff(oldList: List<T>, newList: List<T>): DiffUtil.DiffResult {
        trace { "calculateDiff(...)" }

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

        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
            return oldItem == newItem
        }
    }

    class KeyDiffCallback<T>(private val keyOf: (T) -> Any?) : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
            return keyOf(oldItem) == keyOf(newItem)
        }

        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
            return oldItem == newItem
        }
    }
}
