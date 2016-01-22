package com.pr0gramm.app.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import com.pr0gramm.app.R;

/**
 */
public class CommentSpacerView extends RelativeLayout {

    private final float lineMargin;
    private final float lineWidth;
    private final int lineColor;
    private int depth;

    private Paint linePaint;

    public CommentSpacerView(Context context) {
        this(context, null);
    }

    public CommentSpacerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs,
                R.styleable.CommentSpacerView, 0, 0);

        try {
            lineMargin = a.getDimension(R.styleable.CommentSpacerView_lineMargin, 10);
            lineWidth = a.getDimension(R.styleable.CommentSpacerView_lineWidth, 2);
            lineColor = a.getColor(R.styleable.CommentSpacerView_lineColor, 1);
            depth = a.getInt(R.styleable.CommentSpacerView_depth, 1);
        } finally {
            a.recycle();
        }

        setWillNotDraw(false);
        initDraw();
    }

    public void setDepth(int depth) {
        this.depth = depth;

        int paddingLeft = (int) (lineMargin * depth);
        setPadding(paddingLeft, getPaddingTop(), getPaddingRight(), getPaddingBottom());

        invalidate();
        requestLayout();
    }

    private void initDraw() {
        linePaint = new Paint();
        linePaint.setColor(lineColor);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(lineWidth);
        linePaint.setPathEffect(new DashPathEffect(new float[]{5, 5}, 0));
    }

    @Override
    @SuppressLint("DrawAllocation")
    protected void onDraw(Canvas canvas) {
        Path path = new Path();
        for (int i = 1; i < depth; i++) {
            float x = i * lineMargin - lineWidth;

            path.moveTo(x, 0);
            path.lineTo(x, getHeight());
        }

        canvas.drawPath(path, linePaint);
    }
}
