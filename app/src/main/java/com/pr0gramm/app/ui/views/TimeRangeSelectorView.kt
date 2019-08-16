package com.pr0gramm.app.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.core.view.isVisible
import com.pr0gramm.app.R
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.observeChange
import com.pr0gramm.app.util.use
import rx.Observable
import rx.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit

class TimeRangeSelectorView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val ALL = TimeUnit.DAYS.toMillis(360 * 10)

    var defaultColor: Int = 0
    var selectedColor: Int = 0

    private val subject: BehaviorSubject<Long> = BehaviorSubject.create()
    val selectedTimeRange: Observable<Long> = subject.distinctUntilChanged()

    private val steps = listOf(
            Step(R.id.view_all, ALL),
            Step(R.id.view_1d, TimeUnit.DAYS.toMillis(1)),
            Step(R.id.view_7d, TimeUnit.DAYS.toMillis(7)),
            Step(R.id.view_1m, TimeUnit.DAYS.toMillis(30)),
            Step(R.id.view_6m, TimeUnit.DAYS.toMillis(6 * 60)),
            Step(R.id.view_1y, TimeUnit.DAYS.toMillis(365)))

    private fun setupView(attrs: AttributeSet?) {
        orientation = HORIZONTAL
        View.inflate(context, R.layout.view_time_range_selector, this)

        // get colors from attributes
        context.theme.obtainStyledAttributes(attrs, R.styleable.TimeRangeSelectorView, 0, 0).use {
            defaultColor = it.getColor(R.styleable.TimeRangeSelectorView_trs_defaultColor, 0)
            selectedColor = it.getColor(R.styleable.TimeRangeSelectorView_trs_selectedColor, 0)
        }

        steps.forEachIndexed { index, step ->
            val view = find<TextView>(step.id)
            step.view = view

            view.setOnClickListener {
                // reset all colors
                steps.forEach { it.view?.setTextColor(defaultColor) }

                // and mark the selected one
                view.setTextColor(selectedColor)

                // and publish the new value
                subject.onNext(step.millis)
            }

            // mark the first one
            view.setTextColor(if (index == 0) selectedColor else defaultColor)
        }
    }

    var maxRangeInMillis: Long by observeChange(ALL) {
        steps.drop(1).forEach { step ->
            step.view?.isVisible = step.millis <= maxRangeInMillis
        }
    }

    init {
        setupView(attrs)
    }

    private class Step(@IdRes val id: Int, val millis: Long, var view: TextView? = null)
}