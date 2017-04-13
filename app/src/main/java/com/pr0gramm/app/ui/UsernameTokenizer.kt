package com.pr0gramm.app.ui

import android.widget.MultiAutoCompleteTextView

import com.google.common.base.CharMatcher

/**
 */
class UsernameTokenizer : MultiAutoCompleteTextView.Tokenizer {
    private val letterMatcher = CharMatcher.inRange('a', 'z')
            .or(CharMatcher.inRange('A', 'Z'))
            .or(CharMatcher.inRange('0', '9'))

    override fun findTokenStart(text: CharSequence, cursor: Int): Int {
        var idx = Math.min(cursor - 1, text.length - 1)
        while (idx > 0 && letterMatcher.matches(text[idx]))
            idx--

        return idx
    }

    override fun findTokenEnd(text: CharSequence, cursor: Int): Int {
        var idx = cursor

        if (idx < text.length && text[idx] == '@')
            idx++

        while (idx < text.length && letterMatcher.matches(text[idx]))
            idx++

        return idx
    }

    override fun terminateToken(text: CharSequence): CharSequence {
        return text
    }
}
