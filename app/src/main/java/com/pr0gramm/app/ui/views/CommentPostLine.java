package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import com.jakewharton.rxbinding.view.RxView;
import com.jakewharton.rxbinding.widget.RxTextView;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.R;
import com.pr0gramm.app.api.meta.MetaService;
import com.pr0gramm.app.ui.LineMultiAutoCompleteTextView;
import com.pr0gramm.app.ui.UsernameAutoCompleteAdapter;
import com.pr0gramm.app.ui.UsernameTokenizer;
import com.pr0gramm.app.util.ViewUtility;

import butterknife.Bind;
import butterknife.ButterKnife;
import rx.Observable;

/**
 */
public class CommentPostLine extends FrameLayout {
    @Bind(R.id.comment_text)
    LineMultiAutoCompleteTextView commentTextView;

    @Bind(R.id.comment_post)
    View postButton;

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
        ButterKnife.bind(this);

        // setup auto complete in the comment view.
        MetaService metaService = Dagger.appComponent(getContext()).metaService();

        // change the anchorViews id so it is unique in the view hierarchy
        View anchorView = findViewById(R.id.auto_complete_popup_anchor);
        anchorView.setId(ViewUtility.generateViewId());

        commentTextView.setAnchorView(anchorView);
        commentTextView.setTokenizer(new UsernameTokenizer());
        commentTextView.setAdapter(new UsernameAutoCompleteAdapter(metaService, getContext(),
                android.R.layout.simple_dropdown_item_1line));

        // The post button is only enabled if we have at least one letter.
        RxTextView.afterTextChangeEvents(commentTextView)
                .map(event -> event.editable().toString().trim().length() > 0)
                .subscribe(postButton::setEnabled);
    }

    /**
     * Observable filled with the comments each time the user clicks on the button
     */
    public Observable<String> comments() {
        return RxView.clicks(postButton)
                .map(event -> commentTextView.getText().toString().trim())
                .filter(text -> !text.isEmpty());
    }

    /**
     * Notified about every text changes after typing
     */
    public Observable<String> textChanges() {
        return RxTextView
                .afterTextChangeEvents(commentTextView)
                .map(event -> event.editable().toString());
    }

    public void clear() {
        commentTextView.setText("");
    }

    public void setCommentDraft(String text) {
        this.commentTextView.setText(text);
    }
}
