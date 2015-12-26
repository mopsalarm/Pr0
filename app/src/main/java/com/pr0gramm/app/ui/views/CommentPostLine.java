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

import butterknife.ButterKnife;
import rx.Observable;

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

        // The post button is only enabled if we have at least one letter.
        RxTextView.afterTextChangeEvents(commentTextView)
                .map(event -> event.editable().toString().trim().length() > 0)
                .startWith(false)
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

    public void clear() {
        commentTextView.setText("");
    }
}
