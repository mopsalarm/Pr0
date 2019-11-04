package com.pr0gramm.app.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.animation.doOnEnd
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.view.isVisible
import com.pr0gramm.app.R
import com.pr0gramm.app.util.dp
import com.pr0gramm.app.util.observeChange
import kotlin.math.roundToInt

class TagCloudContainerView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    var maxHeight: Int by observeChange(dp(64)) { requestLayout() }
    var moreThreshold: Int by observeChange(dp(64)) { requestLayout() }

    private var animator: ValueAnimator? = null

    private var state: State = State.COLLAPSED

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)

        if (child.id == R.id.expander) {
            child.setOnClickListener {
                expand()
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

            this.animator?.cancel()
            this.animator = null

            if (state.getBoolean("expanded", false)) {
                this.state = State.EXPANDED
            } else {
                this.state = State.COLLAPSED
            }
        }
    }

    private fun expand() {
        if (state != State.COLLAPSED) {
            return
        }

        state = State.EXPANDING

        // cancel any active animation
        animator?.cancel()

        // create a new animator
        val va = ValueAnimator.ofFloat(0f, 1f)

        // store animator in case we want to cancel it
        animator = va

        va.addUpdateListener { _ ->
            expanderView().alpha = 1f - va.animatedFraction
            requestLayout()
        }

        va.doOnEnd {
            state = State.EXPANDED
            expanderView().isVisible = false
        }

        va.duration = 250L

        va.start()
    }

    /**
     * Resets the view back to its collapsed version.
     * This is not running any animations.
     */
    fun reset() {
        animator?.cancel()

        state = State.COLLAPSED
        expanderView().alpha = 1f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val contentView = contentView()
        val expanderView = expanderView()

        val contentViewHeightSpec = if (state == State.COLLAPSED) {
            MeasureSpec.makeMeasureSpec(moreThreshold + dp(8), MeasureSpec.AT_MOST)
        } else {
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        }

        // calculate desired content height
        contentView.measure(widthMeasureSpec, contentViewHeightSpec)

        var expanderHeight = 0

//        if (contentView is RecyclerView) {
//            val maxChildrenBottom = contentView.children.map { it.bottom }.max()
//            if (maxChildrenBottom != null) {
//                if (maxChildrenBottom + contentView.paddingBottom < measuredContentHeight) {
//                    measuredContentHeight = maxChildrenBottom + contentView.paddingBottom
//                }
//            }
//        }

        // test if we should show the expander.
        val shouldShowExpander = (state == State.EXPANDING || contentView.measuredHeight > moreThreshold) && state != State.EXPANDED

        if (expanderView.isVisible != shouldShowExpander) {
            expanderView.isVisible = shouldShowExpander
        }

        if (shouldShowExpander) {
            // measure the expander view
            expanderView.measure(
                    MeasureSpec.makeMeasureSpec(contentView.measuredWidth, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))

            expanderHeight = expanderView.measuredHeight
        }

        // calculate height
        val heightCollapsed = contentView.measuredHeight.coerceAtMost(maxHeight) + expanderHeight
        val heightExpanded = contentView.measuredHeight

        val animationValue = currentAnimationValue()

        val heightCurrent = if (shouldShowExpander) {
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
            State.EXPANDING -> animator?.animatedFraction ?: 0f
        }
    }

    private fun contentView() = children.first { it.id != R.id.expander }
    private fun expanderView() = children.first { it.id == R.id.expander }

    enum class State {
        COLLAPSED, EXPANDING, EXPANDED
    }
}