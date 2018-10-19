package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.inSpans
import com.pr0gramm.app.UserClasses
import com.pr0gramm.app.util.getColorCompat


/**
 */
class UsernameView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        maxLines = 1

        if (isInEditMode) {
            setUsername("Mopsalarm", 2)
        }
    }

    @SuppressLint("SetTextI18n")
    fun setUsername(name: String, mark: Int) {
        if (mark !in UserClasses.MarkColors.indices) {
            this.text = name
            return
        }

        val symbol = UserClasses.MarkSymbol[mark]
        val color = context.getColorCompat(UserClasses.MarkColors[mark])

        this.text = SpannableStringBuilder().apply {
            append(name)
            append("\u2009")

            inSpans(ForegroundColorSpan(color)) {
                append(symbol)
            }
        }
    }
}
