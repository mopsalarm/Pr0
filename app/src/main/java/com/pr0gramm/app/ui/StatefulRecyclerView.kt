package com.pr0gramm.app.ui

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import com.pr0gramm.app.ui.views.TagsView

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

