package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.R;

import java.util.List;

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
        int padding = getContext().getResources()
                .getDimensionPixelOffset(R.dimen.user_type_drawable_padding);

        setCompoundDrawablePadding(padding);

        if (isInEditMode())
            setUsername("Mopsalarm", 2);
    }

    public void setMark(int mark) {
        if (mark < 0 || mark >= states.size())
            mark = 4;

        // get the drawable for that mark
        setCompoundDrawablesWithIntrinsicBounds(0, 0, states.get(mark), 0);
    }

    public void setUsername(String name, int mark) {
        setText(name);
        setMark(mark);
    }

    private static final List<Integer> states = ImmutableList.of(
            R.drawable.user_type_schwuchtel_small,
            R.drawable.user_type_neuschwuchtel_small,
            R.drawable.user_type_altschwuchtel_small,
            R.drawable.user_type_admin_small,
            R.drawable.user_type_gesperrt_small,
            R.drawable.user_type_moderator_small,
            R.drawable.user_type_fliesentisch_small,
            R.drawable.user_type_legende_small,
            R.drawable.user_type_wichtler_small,
            R.drawable.user_type_pr0mium_small);
}
