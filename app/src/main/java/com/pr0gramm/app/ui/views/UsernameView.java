package com.pr0gramm.app.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import com.pr0gramm.app.UserClasses;

/**
 */
public class UsernameView extends TextView {
    public UsernameView(Context context) {
        super(context);
        init();
    }

    public UsernameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public UsernameView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        if (isInEditMode())
            setUsername("Mopsalarm", 2);
    }

    public void setMark(int mark) {
        if (mark < 0 || mark >= UserClasses.MarkDrawables.size())
            mark = 4;

        // get the drawable for that mark
        setCompoundDrawablesWithIntrinsicBounds(0, 0, UserClasses.MarkDrawables.get(mark), 0);
    }

    @SuppressLint("SetTextI18n")
    public void setUsername(String name, int mark) {
        setText(name + " ");
        setMark(mark);
    }
}
