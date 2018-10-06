package com.pr0gramm.app.ui.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView


class RecyclerViewCompatibleTextView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AppCompatTextView(context, attrs, defStyleAttr) {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // workaround for bug https://issuetracker.google.com/issues/37095917
        // This will make text views in RecyclerViews selectable.
        if (isEnabled) {
            isEnabled = false
            isEnabled = true
        }
    }
}
