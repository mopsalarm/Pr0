package com.pr0gramm.app.ui

import android.content.Context
import android.view.View

class ConservativeLinearLayoutManager(context: Context, orientation: Int, reverseLayout: Boolean) :
        androidx.recyclerview.widget.LinearLayoutManager(context, orientation, reverseLayout) {

    override fun onMeasure(recycler: androidx.recyclerview.widget.RecyclerView.Recycler, state: androidx.recyclerview.widget.RecyclerView.State,
                           widthSpec: Int, heightSpec: Int) {

        val newHeightSpec = View.MeasureSpec.makeMeasureSpec(
                View.MeasureSpec.getSize(heightSpec),
                View.MeasureSpec.UNSPECIFIED)

        super.onMeasure(recycler, state, widthSpec, newHeightSpec)
    }
}