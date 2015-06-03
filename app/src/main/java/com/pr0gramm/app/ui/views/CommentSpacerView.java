package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;

import com.pr0gramm.app.R;

/**
 * Created by jakob on 01.06.15.
 */
public class CommentSpacerView extends View {

    private float lineMargin;
    private float lineWidth;
    private int lineColor;
    private int depth;

    private Paint linePaint;

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

        initDraw();
    }

    public void setDepth(int depth) {
        this.depth = depth;
        invalidate();
        requestLayout();
    }

    public void initDraw() {
        linePaint = new Paint();
        linePaint.setColor(lineColor);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(lineWidth);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float x;
        for (int i = 1; i < depth; i++) {
            x = i * lineMargin - lineWidth;
            canvas.drawLine(x, 0, x, getHeight(), linePaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = MeasureSpec.getSize(heightMeasureSpec);

        float width = (depth - 1) * lineMargin;

        setMeasuredDimension((int) width, height);
    }
}
