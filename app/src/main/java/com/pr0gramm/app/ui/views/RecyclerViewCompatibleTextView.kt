package com.pr0gramm.app.ui.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.pr0gramm.app.util.NonCrashingLinkMovementMethod


class RecyclerViewCompatibleTextView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        movementMethod = NonCrashingLinkMovementMethod
    }
}
