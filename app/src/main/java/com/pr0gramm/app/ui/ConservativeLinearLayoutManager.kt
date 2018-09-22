package com.pr0gramm.app.ui

import android.content.Context
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View

class ConservativeLinearLayoutManager(context: Context, orientation: Int, reverseLayout: Boolean) :
        LinearLayoutManager(context, orientation, reverseLayout) {

    override fun onMeasure(recycler: RecyclerView.Recycler, state: RecyclerView.State,
                           widthSpec: Int, heightSpec: Int) {

        val newHeightSpec = View.MeasureSpec.makeMeasureSpec(
                View.MeasureSpec.getSize(heightSpec),
                View.MeasureSpec.UNSPECIFIED)

        super.onMeasure(recycler, state, widthSpec, newHeightSpec)
    }
}