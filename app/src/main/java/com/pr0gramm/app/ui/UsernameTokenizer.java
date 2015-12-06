package com.pr0gramm.app.ui;

import android.widget.MultiAutoCompleteTextView;

import com.google.common.base.CharMatcher;

/**
 */
public class UsernameTokenizer implements MultiAutoCompleteTextView.Tokenizer {
    private final CharMatcher letterMatcher = CharMatcher.inRange('a', 'z')
            .or(CharMatcher.inRange('A', 'Z'))
            .or(CharMatcher.inRange('0', '9'));

    @Override
    public int findTokenStart(CharSequence text, int cursor) {
        int idx = Math.min(cursor - 1, text.length() - 1);
        while (idx > 0 && letterMatcher.matches(text.charAt(idx)))
            idx--;

        return idx;
    }

    @Override
    public int findTokenEnd(CharSequence text, int cursor) {
        int idx = cursor;

        if (idx < text.length() && text.charAt(idx) == '@')
            idx++;

        while (idx < text.length() && letterMatcher.matches(text.charAt(idx)))
            idx++;

        return idx;
    }

    @Override
    public CharSequence terminateToken(CharSequence text) {
        return text;
    }
}
