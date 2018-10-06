package com.pr0gramm.app.ui

import android.content.Context
import android.text.Layout
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView
import com.pr0gramm.app.ui.views.adjustImeOptions
import com.pr0gramm.app.ui.views.handlePlainTextPaste
import com.pr0gramm.app.util.AndroidUtility

/**
 */
class LineMultiAutoCompleteTextView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = androidx.appcompat.R.attr.autoCompleteTextViewStyle
) : AppCompatMultiAutoCompleteTextView(context, attrs, defStyleAttr) {

    private var anchorView: View? = null

    init {
        // fix auto complete
        val inputType = inputType and EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE.inv()
        setRawInputType(inputType)

        adjustImeOptions(this)

        addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                val layout: Layout? = layout
                val anchorView = anchorView

                if (anchorView != null && layout != null) {
                    val line = layout.getLineForOffset(selectionStart)

                    val lineTop = layout.getLineTop(line)
                    val lineBottom = layout.getLineBottom(line)

                    // reposition the margin
                    val params = FrameLayout.LayoutParams(width, lineBottom - lineTop)
                    params.topMargin = top + totalPaddingTop + lineTop
                    params.rightMargin = left
                    anchorView.layoutParams = params

                    dropDownVerticalOffset = AndroidUtility.dp(context, 5)
                }
            }
        })
    }

    fun setAnchorView(anchor: View?) {
        if (anchor != null) {
            require(anchor.id != View.NO_ID) {
                "Anchor view must have an id."
            }

            require(anchor.parent === parent) {
                "Anchor view must have the same parent"
            }

            require(parent is FrameLayout) {
                "Parent must be a FrameLayout."
            }

            anchorView = anchor
            dropDownAnchor = anchor.id
        } else {
            anchorView = null
            dropDownAnchor = View.NO_ID
        }
    }

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
