package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.pr0gramm.app.R;

/**
 */
public class CommentPostLine extends FrameLayout {
    private EditText commentTextView;
    private View postButton;

    public CommentPostLine(Context context) {
        super(context);
        init();
    }

    public CommentPostLine(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CommentPostLine(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.write_comment_layout, this);
        commentTextView = (EditText) findViewById(R.id.comment_text);
        postButton = findViewById(R.id.comment_post);

        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public EditText getCommentTextView() {
        return commentTextView;
    }

    public View getPostButton() {
        return postButton;
    }
}
