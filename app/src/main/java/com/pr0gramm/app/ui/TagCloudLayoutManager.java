package com.pr0gramm.app.ui;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

/**
 * Tries to display tags in multiple line using a defined spacing.
 * It "works" but it is not nice, I guess.
 */
public class TagCloudLayoutManager extends RecyclerView.LayoutManager {
    private final int gapX, gapY;

    public TagCloudLayoutManager(int gapX, int gapY) {
        this.gapX = gapX;
        this.gapY = gapY;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.WRAP_CONTENT,
                RecyclerView.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        detachAndScrapAttachedViews(recycler);

        int top = 0, left = 0;
        for (int idx = 0; idx < state.getItemCount(); idx++) {
            View view = recycler.getViewForPosition(idx);
            removeMarginsFromView(view);
            addView(view);

            measureChildWithMargins(view, 0, 0);
            int width = getDecoratedMeasuredWidth(view);
            int height = getDecoratedMeasuredHeight(view);

            if (left + width > getWidth() && left > 0) {
                left = 0;
                top += height + gapY;
            }

            layoutDecorated(view, left, top, left + width, top + height);
            left += width + gapX;
        }
    }

    @Override
    public void onMeasure(RecyclerView.Recycler recycler, RecyclerView.State state, int widthSpec, int heightSpec) {
        int parentWidth = View.MeasureSpec.getSize(widthSpec);

        int left = 0, top = 0, height = 0;
        for (int idx = 0; idx < getItemCount(); idx++) {
            Size size = measureScrapChild(recycler, idx,
                    View.MeasureSpec.makeMeasureSpec(idx, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(idx, View.MeasureSpec.UNSPECIFIED));

            if (size != null) {

                if (left + size.width > getWidth() && left > 0) {
                    left = 0;
                    top += size.height + gapY;
                }

                left += size.width + gapX;
                height = Math.max(height, top + size.height);
            }
        }

        setMeasuredDimension(parentWidth, height);
    }

    @Override
    public void onAdapterChanged(RecyclerView.Adapter oldAdapter, RecyclerView.Adapter newAdapter) {
        removeAllViews();
    }

    /**
     * Removes all margins from the given view.
     */
    private static void removeMarginsFromView(View view) {
        ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).setMargins(0, 0, 0, 0);
    }

    private Size measureScrapChild(RecyclerView.Recycler recycler, int position,
                                   int widthSpec, int heightSpec) {

        Size result = null;
        View view = recycler.getViewForPosition(position);
        if (view != null) {
            RecyclerView.LayoutParams p = (RecyclerView.LayoutParams) view.getLayoutParams();
            int childWidthSpec = ViewGroup.getChildMeasureSpec(widthSpec,
                    getPaddingLeft() + getPaddingRight(), p.width);

            int childHeightSpec = ViewGroup.getChildMeasureSpec(heightSpec,
                    getPaddingTop() + getPaddingBottom(), p.height);

            view.measure(childWidthSpec, childHeightSpec);

            result = new Size(view.getMeasuredWidth(), view.getMeasuredHeight());
            recycler.recycleView(view);
        }

        return result;
    }

    private static class Size {
        final int width, height;

        public Size(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
