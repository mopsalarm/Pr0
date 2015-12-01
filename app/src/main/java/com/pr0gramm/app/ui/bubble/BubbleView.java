package com.pr0gramm.app.ui.bubble;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.TextView;

import com.pr0gramm.app.R;
import com.pr0gramm.app.util.AndroidUtility;

import static java.util.Arrays.asList;

/**
 * A view that shows a bubble background
 */
public class BubbleView extends TextView {

    private BubbleDrawable background;

    public BubbleView(Context context) {
        super(context);
        initView(null);
    }

    public BubbleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(attrs);
    }

    public BubbleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public BubbleView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initView(attrs);
    }

    public BubbleView(Context context, int gravity, int bubbleColor) {
        super(context);
        initView(gravity, bubbleColor);
    }

    private void initView(AttributeSet attrs) {
        int gravity = Gravity.LEFT;
        int bubbleColor = 0;

        if (attrs != null) {
            TypedArray a = getContext().getTheme().obtainStyledAttributes(
                    attrs, R.styleable.BubbleView, 0, 0);

            try {
                gravity = a.getInteger(R.styleable.BubbleView_bv_gravity, gravity);
                bubbleColor = a.getColor(R.styleable.BubbleView_bv_bubbleColor, bubbleColor);
            } finally {
                a.recycle();
            }
        }

        initView(gravity, bubbleColor);
    }

    private void initView(int gravity, int bubbleColor) {
        background = new BubbleDrawable(getContext(), gravity, bubbleColor);

        //noinspection deprecation
        setBackgroundDrawable(background);

        switch (gravity) {
            case Gravity.BOTTOM:
                setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), getPaddingBottom() + background.markerSize);
                break;

            case Gravity.TOP:
                setPadding(getPaddingLeft(), getPaddingTop() + background.markerSize, getPaddingRight(), getPaddingBottom());
                break;

            case Gravity.LEFT:
                setPadding(getPaddingLeft() + background.markerSize, getPaddingTop(), getPaddingRight(), getPaddingBottom());
                break;

            case Gravity.RIGHT:
                setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight() + background.markerSize, getPaddingBottom());
                break;
        }
    }

    private static class BubbleDrawable extends Drawable {
        final int gravity;
        final int radius;
        final int markerSize;
        final Paint paint;
        final Path markerPath;

        public BubbleDrawable(Context context, int gravity, int bubbleColor) {
            this.gravity = gravity;
            this.radius = AndroidUtility.dp(context, 6);
            this.markerSize = AndroidUtility.dp(context, 10);

            this.paint = new Paint();
            this.paint.setAntiAlias(true);
            this.paint.setColor(bubbleColor);

            if (!asList(Gravity.BOTTOM, Gravity.RIGHT, Gravity.LEFT, Gravity.TOP).contains(gravity))
                throw new IllegalArgumentException("Invalid gravity given");

            markerPath = new Path();
            markerPath.moveTo(0, 0);
            markerPath.lineTo(-markerSize - 1, markerSize + 1);
            markerPath.lineTo(markerSize + 1, markerSize + 1);
            markerPath.close();
        }

        @Override
        public void draw(Canvas canvas) {
            RectF rect = new RectF(getBounds());

            PointF triMark;
            int triAngle;
            switch (gravity) {
                case Gravity.BOTTOM:
                    triAngle = 180;
                    triMark = new PointF(rect.centerX(), rect.bottom);
                    rect.bottom -= markerSize;
                    break;

                case Gravity.TOP:
                    triAngle = 0;
                    triMark = new PointF(rect.centerX(), rect.top);
                    rect.top += markerSize;
                    break;

                case Gravity.LEFT:
                    triAngle = -90;
                    triMark = new PointF(rect.left, rect.centerY());
                    rect.left += markerSize;
                    break;

                case Gravity.RIGHT:
                    triAngle = 90;
                    triMark = new PointF(rect.right, rect.centerY());
                    rect.right -= markerSize;
                    break;

                default:
                    throw new IllegalStateException("Could not render background");
            }

            if (rect.width() <= 0 || rect.height() <= 0)
                return;

            canvas.drawRoundRect(rect, radius, radius, paint);

            canvas.save();
            try {
                canvas.translate(triMark.x, triMark.y);
                canvas.rotate(triAngle);
                canvas.drawPath(markerPath, paint);
            } finally {
                canvas.restore();
            }
        }

        @Override
        public void setAlpha(int alpha) {
            // do nothing
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            // do nothing
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
