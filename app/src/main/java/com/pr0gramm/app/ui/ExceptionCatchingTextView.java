package com.pr0gramm.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;

import com.pr0gramm.app.util.AndroidUtility;

/**
 */
public class ExceptionCatchingTextView extends AppCompatTextView {
    public ExceptionCatchingTextView(Context context) {
        super(context);
    }

    public ExceptionCatchingTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExceptionCatchingTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } catch (Exception error) {
            AndroidUtility.logToCrashlytics(error);

            setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                    getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
        }
    }

    @Override
    public void draw(Canvas canvas) {
        try {
            super.draw(canvas);
        } catch (Exception error) {
            AndroidUtility.logToCrashlytics(error);
        }
    }

    @Override
    public boolean onPreDraw() {
        try {
            return super.onPreDraw();
        } catch (Exception error) {
            AndroidUtility.logToCrashlytics(error);
            return true;
        }
    }

    @Override
    public int getBaseline() {
        try {
            return super.getBaseline();
        } catch (Exception error) {
            AndroidUtility.logToCrashlytics(error);
            return -1;
        }
    }
}
