package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.inSpans
import com.pr0gramm.app.UserClassesService
import com.pr0gramm.app.util.di.injector
import rx.Observable


/**
 */
class UsernameView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        AppCompatTextView(context, attrs, defStyleAttr) {

    private val userClassesService: UserClassesService = if (isInEditMode) {
        UserClassesService(Observable.empty())
    } else {
        context.injector.instance()
    }

    init {
        maxLines = 1

        if (isInEditMode) {
            setUsername("Mopsalarm", 1)
        }
    }

    @SuppressLint("SetTextI18n")
    fun setUsername(name: String, mark: Int) {
        val userClass = userClassesService.get(mark)

        this.text = SpannableStringBuilder().apply {
            append(name)
            append("\u2009")

            inSpans(ForegroundColorSpan(userClass.color)) {
                append(userClass.symbol)
            }
        }
    }
}
