package com.pr0gramm.app.ui

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import com.pr0gramm.app.ui.views.TagsView
import com.pr0gramm.app.util.observeChangeEx

class StatefulRecyclerView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : androidx.recyclerview.widget.RecyclerView(context, attrs, defStyleAttr) {

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
}

