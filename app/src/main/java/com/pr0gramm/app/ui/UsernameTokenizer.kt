package com.pr0gramm.app.ui

import android.widget.MultiAutoCompleteTextView

/**
 */
class UsernameTokenizer : MultiAutoCompleteTextView.Tokenizer {

    override fun findTokenStart(text: CharSequence, cursor: Int): Int {
        var idx = Math.min(cursor - 1, text.length - 1)
        while (idx > 0 && isLetterOrNumeric(text[idx]))
            idx--

        return idx
    }

    override fun findTokenEnd(text: CharSequence, cursor: Int): Int {
        var idx = cursor

        if (idx < text.length && text[idx] == '@')
            idx++

        while (idx < text.length && isLetterOrNumeric(text[idx]))
            idx++

        return idx
    }

    override fun terminateToken(text: CharSequence): CharSequence {
        return text
    }

    private fun isLetterOrNumeric(str: Char): Boolean {
        return str in 'a'..'z' || str in 'A'..'Z' || str in '0'..'9'
    }
}
