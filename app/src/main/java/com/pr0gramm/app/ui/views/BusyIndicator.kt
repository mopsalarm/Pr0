package com.pr0gramm.app.ui.views

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.pnikosis.materialishprogress.ProgressWheel
import com.pr0gramm.app.services.ThemeHelper.accentColor

/**
 * A busy indicator in the apps accent color.
 */
class BusyIndicator @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        ProgressWheel(context, attrs) {

    init {
        barColor = ContextCompat.getColor(context, accentColor)
        spin()
    }
}
