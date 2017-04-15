package com.pr0gramm.app.ui

import android.content.Context
import android.support.v7.widget.AppCompatMultiAutoCompleteTextView
import android.text.Layout
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import com.google.common.base.Preconditions.checkArgument
import com.pr0gramm.app.util.AndroidUtility

/**
 */
class LineMultiAutoCompleteTextView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatMultiAutoCompleteTextView(context, attrs, defStyleAttr) {

    private var anchorView: View? = null

    init {
        // fix auto complete
        val inputType = inputType and EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE.inv()
        setRawInputType(inputType)

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
            checkArgument(anchor.id != View.NO_ID, "Anchor view must have an id.")
            checkArgument(anchor.parent === parent, "Anchor view must have the same parent")
            checkArgument(parent is FrameLayout, "Parent must be a FrameLayout.")

            anchorView = anchor
            dropDownAnchor = anchor.id
        } else {
            anchorView = null
            dropDownAnchor = View.NO_ID
        }
    }
}
