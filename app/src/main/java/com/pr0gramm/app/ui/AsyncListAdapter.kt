package com.pr0gramm.app.ui

import android.support.v7.util.DiffUtil
import android.support.v7.util.ListUpdateCallback
import android.support.v7.widget.RecyclerView
import com.pr0gramm.app.util.observeChangeEx
import com.pr0gramm.app.util.observeOnMain
import com.pr0gramm.app.util.subscribeOnBackground
import rx.Observable
import rx.subjects.PublishSubject

private const val detectMoves = false

abstract class AsyncListAdapter<T: Any, V : RecyclerView.ViewHolder>(
        private val diffCallback: DiffUtil.ItemCallback<T>) : RecyclerView.Adapter<V>() {

    private val updateSubject: PublishSubject<List<T>> = PublishSubject.create()
    val updates = updateSubject as Observable<List<T>>

    var items: List<T> by observeChangeEx(listOf()) { old, new -> updateSubject.onNext(new) }
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
    open fun submitList(newList: List<T>) {
        if (newList === items) {
            // nothing to do
            return
        }

        // incrementing generation means any currently-running diffs are discarded when they finish
        val runGeneration = ++maxScheduledGeneration

        // fast simple remove all
        if (newList.isEmpty()) {
            val countRemoved = items.size
            items = emptyList()
            // notify last, after list is updated
            notifyItemRangeRemoved(0, countRemoved)
            return
        }

        // fast simple first insert
        if (items.isEmpty()) {
            items = newList
            // notify last, after list is updated
            notifyItemRangeInserted(0, newList.size)
            return
        }

        val oldList = items

        // short list, do the diff in the current thread
        if (oldList.size < 32 && items.size < 32) {
            val diff = calculateDiff(oldList, newList)
            latchList(newList, diff)
            return
        }

        Observable
                .fromCallable { calculateDiff(oldList, newList) }
                .subscribeOnBackground()
                .observeOnMain()
                .subscribe { result ->
                    if (maxScheduledGeneration == runGeneration) {
                        latchList(newList, result)
                    }
                }
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

    private fun latchList(newList: List<T>, diffResult: DiffUtil.DiffResult) {
        items = newList
        diffResult.dispatchUpdatesTo(this)
    }
}
