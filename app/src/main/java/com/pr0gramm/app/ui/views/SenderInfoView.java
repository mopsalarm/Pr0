package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.pr0gramm.app.R;

import org.joda.time.Instant;

import static com.pr0gramm.app.util.AndroidUtility.findView;
import static net.danlew.android.joda.DateUtils.getRelativeTimeSpanString;

/**
 */
public class SenderInfoView extends LinearLayout {
    private final UsernameView nameView;
    private final TextView pointsView;
    private final View pointsUnknownView;
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
        nameView = findView(this, R.id.username);
        pointsView = findView(this, R.id.points);
        pointsUnknownView = findView(this, R.id.points_unknown);
        dateView = findView(this, R.id.date);
        answerView = findView(this, R.id.answer);
        badgeOpView = findView(this, R.id.badge_op);

        setBadgeOpVisible(false);
        setOnAnswerClickedListener(null);
        hidePointView();

        setSingleLine(false);
    }

    public void setPoints(int points) {
        setPoints(points, null);
    }

    private void setPoints(int points, OnLongClickListener listener) {
        String text = getContext().getString(R.string.points, points);
        pointsView.setText(text);
        pointsView.setVisibility(VISIBLE);
        if (listener != null) {
            pointsView.setOnLongClickListener(listener);
        } else {
            pointsView.setLongClickable(false);
        }

        pointsUnknownView.setVisibility(GONE);
    }

    public void setPoints(CommentScore commentScore) {
        setPoints(commentScore.score, v -> {
            String msg = String.format("%d up, %d down", commentScore.up, commentScore.down);
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    public void hidePointView() {
        pointsView.setVisibility(GONE);
        pointsUnknownView.setVisibility(GONE);
    }

    public void setPointsUnknown() {
        pointsView.setVisibility(GONE);
        pointsUnknownView.setVisibility(VISIBLE);
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

    public void setOnAnswerClickedListener(OnClickListener onClickListener) {
        answerView.setVisibility(onClickListener != null ? VISIBLE : GONE);
        answerView.setOnClickListener(onClickListener);
    }

    public void setSenderName(String name, int mark) {
        nameView.setUsername(name, mark);
    }

    public void setOnSenderClickedListener(OnClickListener onClickListener) {
        nameView.setOnClickListener(onClickListener);
    }

    public void setSingleLine(boolean singleLine) {
        setOrientation(singleLine ? HORIZONTAL : VERTICAL);
    }
}
