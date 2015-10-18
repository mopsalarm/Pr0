package com.pr0gramm.app.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.pr0gramm.app.R;

/**
 */
public class ScoreUnknownView extends View {
    private final float radius;
    private final int color;
    private Paint linePaint;

    public ScoreUnknownView(Context context) {
        this(context, null);
    }

    public ScoreUnknownView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs,
                R.styleable.ScoreUnknownView, 0, 0);

        try {
            radius = a.getDimension(R.styleable.ScoreUnknownView_radius, 10);
            color = a.getColor(R.styleable.ScoreUnknownView_fillColor, 0x808080);
        } finally {
            a.recycle();
        }

        setWillNotDraw(false);
        initDraw();
    }

    private void initDraw() {
        linePaint = new Paint();
        linePaint.setColor(color);
        linePaint.setAntiAlias(true);
        linePaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension((int) Math.ceil(6 * radius), (int) Math.ceil(2 * radius));
    }

    @Override
    @SuppressLint("DrawAllocation")
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float y = radius;

        Path path = new Path();
        path.addCircle(1 * radius, y, 0.9f * radius, Path.Direction.CW);
        path.addCircle(3 * radius, y, 0.9f * radius, Path.Direction.CW);
        path.addCircle(5 * radius, y, 0.9f * radius, Path.Direction.CW);

        canvas.drawPath(path, linePaint);
    }
}
