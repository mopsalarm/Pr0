package com.pr0gramm.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.Vote;

/**
 * A plus and a minus sign to handle votes.
 */
public class VoteView extends LinearLayout {
    private final Pr0grammFontTextView viewRateUp;
    private final Pr0grammFontTextView viewRateDown;
    private final Settings settings;

    private ColorStateList markedColor;
    private ColorStateList defaultColor;

    private OnVoteListener onVoteListener;
    private Vote state;

    private FeedItem feedItem;

    public VoteView(Context context) {
        this(context, null);
    }

    public VoteView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VoteView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        settings = Settings.of(context);

        int orientation = 0, spacing = 0, textSize = 24;
        markedColor = ColorStateList.valueOf(context.getResources().getColor(R.color.primary));
        defaultColor = ColorStateList.valueOf(context.getResources().getColor(R.color.white));

        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.VoteView,
                    0, 0);

            try {
                orientation = a.getInteger(R.styleable.VoteView_orientation, orientation);
                spacing = a.getDimensionPixelOffset(R.styleable.VoteView_spacing, spacing);
                markedColor = a.getColorStateList(R.styleable.VoteView_markedColor);
                defaultColor = a.getColorStateList(R.styleable.VoteView_defaultColor);
                textSize = a.getDimensionPixelSize(R.styleable.VoteView_textSize, textSize);

            } finally {
                a.recycle();
            }
        }

        setOrientation(orientation == 1 ? VERTICAL : HORIZONTAL);

        // initialize vote up view
        viewRateUp = new Pr0grammFontTextView(context);
        viewRateUp.setText("+");
        viewRateUp.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        viewRateUp.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // initialize vote down view.
        viewRateDown = new Pr0grammFontTextView(context);
        viewRateDown.setText("-");
        viewRateDown.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        viewRateDown.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // add views
        addView(viewRateUp);
        addView(viewRateDown);

        // add padding between the views
        if (spacing > 0) {
            View view = new View(context);
            view.setLayoutParams(new ViewGroup.LayoutParams(spacing, spacing));
            addView(view, 1);
        }

        // set initial voting state
        setVote(Vote.NEUTRAL, true);

        // register listeners
        viewRateUp.setOnClickListener(v -> {
            setVote((state == Vote.UP || state == Vote.FAVORITE) ? Vote.NEUTRAL : Vote.UP);
        });

        viewRateDown.setOnClickListener(v -> {
            setVote(state == Vote.DOWN ? Vote.NEUTRAL : Vote.DOWN);
        });
    }

    public OnVoteListener getOnVoteListener() {
        return onVoteListener;
    }

    public void setOnVoteListener(OnVoteListener onVoteListener) {
        this.onVoteListener = onVoteListener;
    }

    public ColorStateList getDefaultColor() {
        return defaultColor;
    }

    public void setDefaultColor(ColorStateList defaultColor) {
        this.defaultColor = defaultColor;
    }

    public ColorStateList getMarkedColor() {
        return markedColor;
    }

    public void setMarkedColor(ColorStateList markedColor) {
        this.markedColor = markedColor;
    }

    public void setVote(Vote vote) {
        setVote(vote, false);
    }

    public void setVote(Vote vote, boolean force) {
        if (state == vote)
            return;

        // check with listener, if we really want to do the vote.
        if (!force && onVoteListener != null && !onVoteListener.onVoteClicked(vote))
            return;

        // set new voting state
        state = vote;

        boolean animated = !force && isAnimated();
        updateVoteViewState(animated);
    }

    public Vote getVote() {
        return state;
    }

    private void updateVoteViewState(boolean animated) {
        final int duration = animated ? 500 : 0;

        if (state == Vote.NEUTRAL) {
            viewRateUp.setTextColor(defaultColor);
            viewRateDown.setTextColor(defaultColor);
            viewRateUp.animate().rotation(0).alpha(1f).setDuration(duration).start();
            viewRateDown.animate().rotation(0).alpha(1f).setDuration(duration).start();
        }

        if (state == Vote.UP || state == Vote.FAVORITE) {
            viewRateUp.setTextColor(markedColor);
            viewRateDown.setTextColor(defaultColor);
            viewRateUp.animate().rotation(360).alpha(1f).setDuration(duration).start();
            viewRateDown.animate().rotation(0).alpha(0.5f).setDuration(duration).start();
        }

        if (state == Vote.DOWN) {
            viewRateUp.setTextColor(defaultColor);
            viewRateDown.setTextColor(markedColor);
            viewRateUp.animate().rotation(0).alpha(0.5f).setDuration(duration).start();
            viewRateDown.animate().rotation(360).alpha(1f).setDuration(duration).start();
        }
    }

    private boolean isAnimated() {
        return settings.animateVoteView();
    }

    /**
     * A listener that reacts to changes in the vote on this view.
     */
    public interface OnVoteListener {
        boolean onVoteClicked(Vote newVote);
    }
}
