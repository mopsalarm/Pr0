package com.pr0gramm.app.ui

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView
import androidx.core.text.getSpans
import androidx.core.text.inSpans
import com.pr0gramm.app.ui.views.adjustImeOptions
import com.pr0gramm.app.ui.views.handlePlainTextPaste
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.logger
import com.pr0gramm.app.util.observeChange

/**
 */
class LineMultiAutoCompleteTextView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = androidx.appcompat.R.attr.autoCompleteTextViewStyle
) : AppCompatMultiAutoCompleteTextView(context, attrs, defStyleAttr) {

    private var anchorView: View? = null
    private var initialized = false
    private var isUpdatingSuffix = false

    var suffix: String? by observeChange(null) { updateSuffix() }

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

        initialized = true
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

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        logger.debug { "Selection changed: $selStart $selEnd" }

        // update the selection to reflect the unchangeable suffix.
        updateSuffix(selStart, selEnd)

        super.onSelectionChanged(selStart, selEnd)
    }

    private fun updateSuffix(selStart: Int? = null, selEnd: Int? = null) {
        if (!initialized || isUpdatingSuffix)
            return

        isUpdatingSuffix = true
        try {
            val text = this.text as? SpannableStringBuilder
            if (text != null) {
                val suffix = this.suffix
                if (suffix == null) {
                    clearSuffix(text)
                } else {
                    updateSuffix(text, suffix)

                    // maximal position to allow to select to
                    val maxSelection = (this.text.length - suffix.length - 1).coerceAtLeast(0)

                    // update selection to the allowed selection range
                    val updatedSelStart = (selStart ?: maxSelection).coerceAtMost(maxSelection)
                    val updatedSelEnd = (selEnd ?: maxSelection).coerceAtMost(maxSelection)
                    if (updatedSelStart != selStart || updatedSelEnd != selEnd) {
                        Selection.setSelection(text, updatedSelStart, updatedSelEnd)
                    }
                }
            }
        } finally {
            isUpdatingSuffix = false
        }
    }

    private fun clearSuffix(text: SpannableStringBuilder) {
        val suffixSpan = text.getSpans<SuffixSpan>().firstOrNull()
        if (suffixSpan != null) {
            val spanStart = text.getSpanStart(suffixSpan)
            val spanEnd = text.getSpanEnd(suffixSpan)

            text.removeSpan(suffixSpan)
            text.replace(spanStart, spanEnd, "")
        }
    }

    private fun updateSuffix(text: SpannableStringBuilder, suffix: String) {
        val suffixSpan = text.getSpans<SuffixSpan>().firstOrNull()
        if (suffixSpan == null) {
            logger.debug { "Adding suffix to text" }
            text.apply {
                // let the text end with a new line before adding the suffix
                if (!endsWith("\n")) {
                    append("\n")
                }

                val styleSpan = StyleSpan(Typeface.ITALIC)
                val colorSpan = ForegroundColorSpan(0xff808080.toInt())
                inSpans(SuffixSpan, colorSpan, styleSpan) {
                    append(suffix)
                }
            }
        } else {
            val spanStart = text.getSpanStart(suffixSpan)
            val spanEnd = text.getSpanEnd(suffixSpan)

            if (text.substring(spanStart, spanEnd) != suffix) {
                logger.debug { "Updating text to suffix: $suffix" }
                text.replace(spanStart, spanEnd, suffix)
            }
        }
    }

    companion object {
        private val logger = logger("LineMultiAutoCompleteTextView")
    }

    private object SuffixSpan
}

