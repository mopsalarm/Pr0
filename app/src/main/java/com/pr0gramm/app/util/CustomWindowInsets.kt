package com.pr0gramm.app.util

import android.view.WindowInsets


data class CustomWindowInsets(val top: Int, val bottom: Int) {
    constructor(insets: WindowInsets) : this(insets.systemWindowInsetTop, insets.systemWindowInsetBottom)
}
