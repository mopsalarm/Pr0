package com.pr0gramm.app.ui.views

import android.content.Context
import android.util.AttributeSet
import com.pnikosis.materialishprogress.ProgressWheel
import com.pr0gramm.app.services.ThemeHelper.accentColor
import com.pr0gramm.app.util.getColorCompat

/**
 * A busy indicator in the apps accent color.
 */
class BusyIndicator @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        ProgressWheel(context, attrs) {

    init {
        barColor = context.getColorCompat(accentColor)
        spin()
    }
}
