package com.pr0gramm.app.ui

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.ui.views.TagsView
import com.pr0gramm.app.util.observeChangeEx

class StatefulRecyclerView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var savedAdapterState: Parcelable? = null
    private var savedHierarchyState: SparseArray<Parcelable>? = null

    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>?) {
        super.dispatchSaveInstanceState(container)

        for (idx in 0 until childCount) {
            val child = getChildAt(idx)
            if (child is TagsView) {
                child.saveHierarchyState(container)
            }
        }
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>?) {
        super.dispatchRestoreInstanceState(container)
        savedHierarchyState = container
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        return bundleOf(
                "viewState" to superState,
                "adapterState" to (adapter as? InstanceStateAware)?.onSaveInstanceState())
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val bundle = state as? Bundle ?: return
        super.onRestoreInstanceState(bundle.getParcelable("viewState"))

        val adapter = this.adapter
        val adapterState = bundle.getParcelable<Parcelable>("adapterState")

        if (this.adapter == null) {
            this.savedAdapterState = adapterState
        } else if (adapter is InstanceStateAware && adapterState != null) {
            this.savedAdapterState = null
            adapter.onRestoreInstanceState(adapterState)
        }
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        super.setAdapter(adapter)

        // restore state if available
        val adapterState = this.savedAdapterState
        if (adapterState != null) {
            if (adapter is InstanceStateAware) {
                adapter.onRestoreInstanceState(adapterState)
            }

            this.savedAdapterState = null
        }
    }

    var primaryScrollListener by observeChangeEx<OnScrollListener?>(null) { oldValue, newValue ->
        if (oldValue != null) {
            super.removeOnScrollListener(oldValue)
        }

        if (newValue != null) {
            super.addOnScrollListener(newValue)
        }
    }

    override fun addOnScrollListener(listener: OnScrollListener) {
        val primaryScrollListener = this.primaryScrollListener

        if (primaryScrollListener != null) {
            // add the new scroll listener and put the primary one at the end.
            super.removeOnScrollListener(primaryScrollListener)
            super.addOnScrollListener(listener)
            super.addOnScrollListener(primaryScrollListener)

        } else {
            super.addOnScrollListener(listener)
        }
    }

    inner class LinearLayoutManager(context: Context?) : androidx.recyclerview.widget.LinearLayoutManager(context) {
        override fun onLayoutCompleted(state: State?) {
            super.onLayoutCompleted(state)

            savedHierarchyState?.let { container ->
                for (idx in 0 until childCount) {
                    val child = getChildAt(idx)
                    child?.restoreHierarchyState(container)
                }
            }

            savedHierarchyState = null
        }
    }

    /**
     * Let the adapter implement this to let the state be saved by
     * the recyclerview
     */
    interface InstanceStateAware {
        fun onSaveInstanceState(): Parcelable?
        fun onRestoreInstanceState(inState: Parcelable)
    }
}

