package com.pr0gramm.app.ui.views

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.support.v7.widget.AppCompatAutoCompleteTextView
import android.support.v7.widget.AppCompatEditText
import android.util.AttributeSet
import android.widget.EditText
import android.widget.TextView

class PlainTextAutoCompleteTextView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : AppCompatAutoCompleteTextView(context, attrs, defStyleAttr) {

    // Intercept and modify the paste event.
    // Let everything else through unchanged.
    override fun onTextContextMenuItem(id: Int): Boolean {
        return if (id == android.R.id.paste) {
            handlePlainTextPaste(this) { super.onTextContextMenuItem(it) }
        } else {
            super.onTextContextMenuItem(id)
        }
    }
}

class PlainEditText @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : AppCompatEditText(context, attrs, defStyleAttr) {

    // Intercept and modify the paste event.
    // Let everything else through unchanged.
    override fun onTextContextMenuItem(id: Int): Boolean {
        return if (id == android.R.id.paste) {
            handlePlainTextPaste(this) { super.onTextContextMenuItem(it) }
        } else {
            super.onTextContextMenuItem(id)
        }
    }
}

inline fun handlePlainTextPaste(view: EditText, superCall: (id: Int) -> Boolean): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        return superCall(android.R.id.pasteAsPlainText)
    }

    // We can use this to know where the text position was originally before we pasted
    val selectionStartPrePaste = view.selectionStart

    // Let the EditText's normal paste routine fire, then modify the content after.
    // This is simpler than re-implementing the paste logic, which we'd have to do
    // if we want to get the text from the clipboard ourselves and then modify it.
    val result = superCall(android.R.id.paste)

    var text: CharSequence = view.text
    var selectionStart = view.selectionStart
    var selectionEnd = view.selectionEnd

    // There is an option in the Chrome mobile app to copy image; however, instead of the
    // image in the form of the uri, Chrome gives us the html source for the image, which
    // the platform paste code turns into the unicode object character. The below section
    // of code looks for that edge case and replaces it with the url for the image.
    val startIndex = selectionStart - 1
    val pasteStringLength = selectionStart - selectionStartPrePaste

    // Only going to handle the case where the pasted object is the image
    if (pasteStringLength == 1 && text[startIndex] == '\uFFFC') {
        val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null) {
            val item = clip.getItemAt(0)
            val sb = StringBuilder(text)
            val url = item.text.toString()
            sb.replace(selectionStartPrePaste, selectionStart, url)
            text = sb.toString()
            selectionStart = selectionStartPrePaste + url.length
            selectionEnd = selectionStart
        }
    }

    // This removes the formatting due to the conversion to string.
    view.setText(text.toString(), TextView.BufferType.EDITABLE)

    // Restore the cursor selection state.
    view.setSelection(selectionStart, selectionEnd)

    return result
}
