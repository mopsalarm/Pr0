package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.meta.MetaService;
import com.pr0gramm.app.ui.LineMultiAutoCompleteTextView;
import com.pr0gramm.app.ui.UsernameAutoCompleteAdapter;
import com.pr0gramm.app.ui.UsernameTokenizer;
import com.pr0gramm.app.util.ViewUtility;

import butterknife.ButterKnife;

/**
 */
public class CommentPostLine extends FrameLayout {
    private LineMultiAutoCompleteTextView commentTextView;
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
        commentTextView = ButterKnife.findById(this, R.id.comment_text);
        postButton = findViewById(R.id.comment_post);

        // setup auto complete in the comment view.
        MetaService metaService = Dagger.appComponent(getContext()).metaService();

        View anchorView = findViewById(R.id.auto_complete_popup_anchor);

        // change the anchorViews id so it is unique in the view hierarchy
        anchorView.setId(ViewUtility.generateViewId());

        commentTextView.setAnchorView(anchorView);
        commentTextView.setTokenizer(new UsernameTokenizer());
        commentTextView.setAdapter(new UsernameAutoCompleteAdapter(metaService, getContext(),
                android.R.layout.simple_dropdown_item_1line));
    }

    public EditText getCommentTextView() {
        return commentTextView;
    }

    public View getPostButton() {
        return postButton;
    }
}
