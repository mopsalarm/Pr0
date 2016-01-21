package com.pr0gramm.app.ui;

import android.content.Context;
import android.support.v7.widget.AppCompatMultiAutoCompleteTextView;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;

import com.pr0gramm.app.util.AndroidUtility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;

/**
 */
public class LineMultiAutoCompleteTextView extends AppCompatMultiAutoCompleteTextView {
    private static final Logger logger = LoggerFactory.getLogger("LineMultiAutoCompleteTextView");
    private View anchorView;

    public LineMultiAutoCompleteTextView(Context context) {
        super(context);
        initialize();
    }

    public LineMultiAutoCompleteTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public LineMultiAutoCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        // fix auto complete
        int inputType = getInputType();
        inputType &= ~EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE;
        setRawInputType(inputType);

        addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Layout layout = getLayout();
                if (anchorView != null && layout != null) {
                    int line = layout.getLineForOffset(getSelectionStart());

                    int lineTop = layout.getLineTop(line);
                    int lineBottom = layout.getLineBottom(line);

                    // reposition the margin
                    ViewGroup.MarginLayoutParams params = new FrameLayout.LayoutParams(getWidth(), lineBottom - lineTop);
                    params.topMargin = getTop() + getTotalPaddingTop() + lineTop;
                    params.rightMargin = getLeft();
                    anchorView.setLayoutParams(params);

                    setDropDownVerticalOffset(AndroidUtility.dp(getContext(), 5));
                }
            }
        });
    }

    public void setAnchorView(View anchor) {
        if(anchor != null) {
            checkArgument(anchor.getId() != NO_ID, "Anchor view must have an id.");
            checkArgument(anchor.getParent() == getParent(), "Anchor view must have the same parent");
            checkArgument(getParent() instanceof FrameLayout, "Parent must be a FrameLayout.");

            anchorView = anchor;
            setDropDownAnchor(anchor.getId());
        } else {
            anchorView = null;
            setDropDownAnchor(NO_ID);
        }
    }
}
