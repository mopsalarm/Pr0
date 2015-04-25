package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pr0gramm.app.R;

import org.joda.time.Instant;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.danlew.android.joda.DateUtils.getRelativeTimeSpanString;

/**
 */
public class SenderInfoView extends LinearLayout {
    private final UsernameView nameView;
    private final TextView pointsView;
    private final TextView dateView;
    private final View answerView;
    private final View badgeOpView;

    public SenderInfoView(Context context) {
        this(context, null, 0, 0);
    }

    public SenderInfoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0);
    }

    public SenderInfoView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SenderInfoView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr);

        inflate(getContext(), R.layout.sender_info, this);
        nameView = findView(R.id.username);
        pointsView = findView(R.id.points);
        dateView = findView(R.id.date);
        answerView = findView(R.id.answer);
        badgeOpView = findView(R.id.badge_op);

        setBadgeOpVisible(false);
        setAnswerClickedListener(null);
        setPointsVisible(false);
        setOrientation(VERTICAL);
    }

    public void setPoints(int points) {
        String text = getContext().getString(R.string.points, points);
        pointsView.setText(text);
    }

    public void setPointsVisible(boolean visible) {
        pointsView.setVisibility(visible ? VISIBLE : GONE);
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T findView(int id) {
        return checkNotNull((T) findViewById(id));
    }

    public void setDate(Instant date) {
        setFormattedDate(getRelativeTimeSpanString(getContext(), date));
    }

    public void setFormattedDate(CharSequence date) {
        dateView.setText(date);
    }

    public void setBadgeOpVisible(boolean visible) {
        badgeOpView.setVisibility(visible ? VISIBLE : GONE);
    }

    public void setAnswerClickedListener(OnClickListener onClickListener) {
        answerView.setVisibility(onClickListener != null ? VISIBLE : GONE);
        answerView.setOnClickListener(onClickListener);
    }

    public void setSenderName(String name, int mark) {
        nameView.setUsername(name, mark);
    }

    public void setOnSenderClickedListener(OnClickListener onClickListener) {
        nameView.setOnClickListener(onClickListener);
    }
}
