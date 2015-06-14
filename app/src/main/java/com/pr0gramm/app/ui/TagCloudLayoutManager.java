package com.pr0gramm.app.ui;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Tries to display tags in multiple line using a defined spacing.
 * It "works" but it is not nice, I guess.
 */
public class TagCloudLayoutManager extends RecyclerView.LayoutManager {
    private static final Logger logger = LoggerFactory.getLogger(TagCloudLayoutManager.class);

    private static final int MAX_NUMBER_OF_ROWS = 3;

    private final int gapX, gapY;
    private Config config = new Config(0, 0, 0);
    private int scrollOffset;

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
        if (config.width <= 0) {
            logger.warn("onLayoutChildren called before onMeasure");
            return;
        }

        int top = 0, left = 0;
        for (int idx = 0; idx < state.getItemCount(); idx++) {
            View view = recycler.getViewForPosition(idx);
            removeMarginsFromView(view);

            Size size = measureChildUnspecified(view);

            if (left + size.width > config.width && left > 0) {
                left = 0;
                top += size.height + gapY;
            }

            addView(view);
            layoutDecorated(view, left, top, left + size.width, top + size.height);
            left += size.width + gapX;
        }

        scrollOffset = Math.min(scrollOffset, computeHorizontalScrollRange(state));
        offsetChildrenHorizontal(-scrollOffset);
    }

    @Override
    public void onMeasure(RecyclerView.Recycler recycler, RecyclerView.State state, int widthSpec, int heightSpec) {
        int parentWidth = View.MeasureSpec.getSize(widthSpec);

        List<Size> sizes = measureAllElements(recycler);

        // estimate the needed with using brute force!
        int width = parentWidth;
        Config config = measureConfig(sizes, width);
        while (config.rows > MAX_NUMBER_OF_ROWS) {
            width += Math.max(10, (int) (width * 0.1));
            config = measureConfig(sizes, width);
        }

        this.config = config;
        setMeasuredDimension(parentWidth, config.height);
    }

    private Config measureConfig(List<Size> sizes, int maxWidth) {
        int left = 0, top = 0, width = 0, height = 0, rows = 1;
        for (int idx = 0; idx < sizes.size(); idx++) {
            Size size = sizes.get(idx);
            if (size != null) {
                if (left + size.width > maxWidth && left > 0) {
                    left = 0;
                    top += size.height + gapY;
                    rows++;
                }

                height = Math.max(height, top + size.height);
                width = Math.max(width, left + size.width);

                left += size.width + gapX;
            }
        }

        return new Config(width, height, rows);
    }

    private static final class Config {
        final int width;
        final int height;
        final int rows;

        public Config(int width, int height, int rows) {
            this.width = width;
            this.height = height;
            this.rows = rows;
        }
    }

    private List<Size> measureAllElements(RecyclerView.Recycler recycler) {
        List<Size> sizes = new ArrayList<>();
        for (int idx = 0; idx < getItemCount(); idx++) {
            View view = recycler.getViewForPosition(idx);
            removeMarginsFromView(view);
            sizes.add(measureChildUnspecified(view));

            detachView(view);
            recycler.recycleView(view);
        }

        return sizes;
    }

    @Override
    public void onAdapterChanged(RecyclerView.Adapter oldAdapter, RecyclerView.Adapter newAdapter) {
        removeAllViews();
    }

    @Override
    public boolean canScrollHorizontally() {
        return true;
    }

    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        int scroll = dx;

        int maxScroll = computeHorizontalScrollRange(state);
        if (scrollOffset + scroll < 0) {
            scroll = -scrollOffset;

        } else if (scrollOffset + scroll > maxScroll) {
            scroll = -(scrollOffset - maxScroll);
        }

        scrollOffset += scroll;
        logger.info("scroll: {}, scroll offset after scrolling: {}", scroll, scrollOffset);

        offsetChildrenHorizontal(-scroll);
        return scroll;
    }

    @Override
    public int computeHorizontalScrollOffset(RecyclerView.State state) {
        return Math.min(scrollOffset, computeHorizontalScrollRange(state));
    }

    @Override
    public int computeHorizontalScrollRange(RecyclerView.State state) {
        return Math.max(0, config.width - getWidth());
    }

    @Override
    public int computeHorizontalScrollExtent(RecyclerView.State state) {
        return Math.max(1, computeHorizontalScrollRange(state) / 10);
    }

    /**
     * Removes all margins from the given view.
     */
    private static void removeMarginsFromView(View view) {
        ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).setMargins(0, 0, 0, 0);
    }

    private static Size measureChildUnspecified(View view) {
        int spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

        RecyclerView.LayoutParams p = (RecyclerView.LayoutParams) view.getLayoutParams();
        int childWidthSpec = ViewGroup.getChildMeasureSpec(spec, 0, p.width);
        int childHeightSpec = ViewGroup.getChildMeasureSpec(spec, 0, p.height);
        view.measure(childWidthSpec, childHeightSpec);

        return new Size(view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static class Size {
        final int width, height;

        public Size(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
