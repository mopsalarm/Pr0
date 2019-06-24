package com.pr0gramm.app.util

import androidx.core.view.WindowInsetsCompat


data class CustomWindowInsets(val top: Int, val bottom: Int) {
    constructor(insets: WindowInsetsCompat) : this(insets.systemWindowInsetTop, insets.systemWindowInsetBottom)
}
