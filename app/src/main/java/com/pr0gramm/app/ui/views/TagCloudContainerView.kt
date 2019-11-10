package com.pr0gramm.app.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.view.isVisible
import com.pr0gramm.app.R
import com.pr0gramm.app.util.dp
import com.pr0gramm.app.util.observeChange
import kotlin.math.roundToInt

private const val AnimationDuration = 250L

class TagCloudContainerView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    var moreOffset: Int by observeChange(0) { requestLayout() }

    private var expandAnimator: ValueAnimator? = null

    var clipHeight: Int by observeChange(0) { requestLayout() }

    private var state: State = State.COLLAPSED

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)

        if (child.id == R.id.expander) {
            child.setOnClickListener {
                animateTo(State.EXPANDED)
            }
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        return bundleOf(
                "superState" to superState,
                "expanded" to (state != State.COLLAPSED))
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            super.onRestoreInstanceState(state.getParcelable("superState"))

            this.expandAnimator?.cancel()
            this.expandAnimator = null

            if (state.getBoolean("expanded", false)) {
                this.state = State.EXPANDED
            } else {
                this.state = State.COLLAPSED
            }
        }
    }

    private fun animateTo(targetState: State) {
        if (state === targetState || state === State.ANIMATING) {
            return
        }

        // cancel any active animation
        expandAnimator?.cancel()

        val valueStart = if (targetState === State.EXPANDED) 0f else 1f
        val valueTarget = if (targetState === State.EXPANDED) 1f else 0f

        // create a new animator
        expandAnimator = ValueAnimator.ofFloat(valueStart, valueTarget).apply {
            addUpdateListener { va ->
                expanderView().alpha = 1f - va.animatedValue as Float
                requestLayout()
            }

            doOnEnd {
                state = targetState
                expanderView().isVisible = targetState === State.COLLAPSED
            }

            doOnStart {
                state = State.ANIMATING
            }

            duration = AnimationDuration

            start()
        }
    }

    /**
     * Resets the view back to its collapsed version.
     * This is not running any animations.
     */
    fun reset(animated: Boolean = false) {
        if (animated) {
            animateTo(State.COLLAPSED)
        } else {
            expandAnimator?.cancel()
            expandAnimator = null

            state = State.COLLAPSED
            expanderView().alpha = 1f
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val contentView = contentView()
        val expanderView = expanderView()

        val contentViewHeightSpec = if (state == State.COLLAPSED) {
            MeasureSpec.makeMeasureSpec(clipHeight + moreOffset + dp(8), MeasureSpec.AT_MOST)
        } else {
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        }

        // calculate desired content height
        contentView.measure(widthMeasureSpec, contentViewHeightSpec)

        var expanderHeight = 0

        // test if we should show the expander.
        val shouldShowExpanderNow = (state === State.ANIMATING || contentView.measuredHeight > (clipHeight + moreOffset)) && state !== State.EXPANDED

        if (expanderView.isVisible != shouldShowExpanderNow) {
            expanderView.isVisible = shouldShowExpanderNow
        }

        if (shouldShowExpanderNow) {
            // measure the expander view
            expanderView.measure(
                    MeasureSpec.makeMeasureSpec(contentView.measuredWidth, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))

            expanderHeight = expanderView.measuredHeight
        }

        // calculate height
        val heightCollapsed = contentView.measuredHeight.coerceAtMost(clipHeight) + expanderHeight
        val heightExpanded = contentView.measuredHeight

        val animationValue = currentAnimationValue()

        val heightCurrent = if (shouldShowExpanderNow) {
            (heightExpanded * animationValue + heightCollapsed * (1 - animationValue)).roundToInt()
        } else {
            heightExpanded
        }

        setMeasuredDimension(contentView.measuredWidthAndState, heightCurrent)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val height = bottom - top
        val width = right - left

        // reserved height for the content
        var heightContent = height

        val expanderView = expanderView()
        if (expanderView.isVisible) {
            // and add the expander view to the bottom
            expanderView.layout(0, bottom - expanderView.measuredHeight, width, height)
            heightContent -= expanderView.measuredHeight * (1 - currentAnimationValue()).roundToInt()
        }

        // fill the view with the contentView
        contentView().layout(0, 0, (right - left), heightContent)
    }

    private fun currentAnimationValue(): Float {
        return when (state) {
            State.COLLAPSED -> 0f
            State.EXPANDED -> 1f
            State.ANIMATING -> expandAnimator?.animatedValue as? Float ?: 0f
        }
    }

    private fun contentView() = children.first { it.id != R.id.expander }
    private fun expanderView() = children.first { it.id == R.id.expander }

    enum class State {
        COLLAPSED, ANIMATING, EXPANDED
    }
}