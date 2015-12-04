package com.pr0gramm.app.ui;

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

public class ConservativeLinearLayoutManager extends LinearLayoutManager {
    public ConservativeLinearLayoutManager(Context context, int orientation, boolean reverseLayout) {
        super(context, orientation, reverseLayout);
    }

    @Override
    public void onMeasure(RecyclerView.Recycler recycler, RecyclerView.State state,
                          int widthSpec, int heightSpec) {

        int newHeightSpec = View.MeasureSpec.makeMeasureSpec(
                View.MeasureSpec.getSize(heightSpec),
                View.MeasureSpec.UNSPECIFIED);

        super.onMeasure(recycler, state, widthSpec, newHeightSpec);
    }
}