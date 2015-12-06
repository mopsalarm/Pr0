package com.pr0gramm.app.ui;

import android.content.Context;
import android.os.Build;
import android.support.v7.widget.AppCompatMultiAutoCompleteTextView;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.AutoCompleteTextView;
import android.widget.ListPopupWindow;

import com.google.common.base.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

import static com.pr0gramm.app.util.AndroidUtility.ifPresent;

/**
 */
public class LineMultiAutoCompleteTextView extends AppCompatMultiAutoCompleteTextView {
    private static final Logger logger = LoggerFactory.getLogger("LineMultiAutoCompleteTextView");

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
        addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Layout layout = getLayout();
                int pos = getSelectionStart();
                int line = layout.getLineForOffset(pos);
                int baseline = layout.getLineBottom(line);

                int bottom = getHeight();
                int padding = getTotalPaddingTop();
                setDropDownVerticalOffset(baseline - bottom + padding);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    ifPresent(getPopup(), popup -> {
                        popup.setDropDownGravity(Gravity.START);
                    });
                }
            }
        });
    }

    private Optional<ListPopupWindow> getPopup() {
        try {
            Field field = AutoCompleteTextView.class.getDeclaredField("mPopup");
            field.setAccessible(true);
            return Optional.fromNullable((ListPopupWindow) field.get(this));

        } catch (Exception error) {
            logger.warn("Could not get the popup");
            return Optional.absent();
        }
    }
}
