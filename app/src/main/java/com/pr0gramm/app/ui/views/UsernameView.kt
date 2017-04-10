package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.support.v7.widget.AppCompatTextView
import android.util.AttributeSet

import com.pr0gramm.app.UserClasses

/**
 */
class UsernameView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        if (isInEditMode) {
            setUsername("Mopsalarm", 2)
        }
    }

    var mark: Int = 4
        set(v) {
            val mark = v.takeUnless { it < 0 || it >= UserClasses.MarkDrawables.size } ?: 4
            setCompoundDrawablesWithIntrinsicBounds(0, 0, UserClasses.MarkDrawables[mark], 0)
        }

    @SuppressLint("SetTextI18n")
    fun setUsername(name: String, mark: Int) {
        this.text = name + " "
        this.mark = mark
    }
}
