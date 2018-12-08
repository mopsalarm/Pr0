package com.pr0gramm.app.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.util.DurationFormat
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.visible

/**
 */
class SenderInfoView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {
    private val nameView: UsernameView
    private val pointsView: TextView
    private val pointsUnknownView: View
    private val dateView: TextView
    private val answerView: View
    private val badgeOpView: View

    init {
        View.inflate(getContext(), R.layout.sender_info, this)
        nameView = find(R.id.username)
        pointsView = find(R.id.points)
        pointsUnknownView = find(R.id.points_unknown)
        dateView = find(R.id.date)
        answerView = find(R.id.answer)
        badgeOpView = find(R.id.badge_op)

        setBadgeOpVisible(false)
        setOnAnswerClickedListener(null)
        hidePointView()

        setSingleLine(false)
    }

    fun setPoints(points: Int) {
        setPoints(points, null)
    }

    fun setPoints(commentScore: CommentScore) {
        setPoints(commentScore.score, commentScore)
    }

    private fun setPoints(points: Int, score: CommentScore?) {
        pointsView.text = context.getString(R.string.points, points)
        pointsView.visible = true

        if (score != null) {
            pointsView.setOnLongClickListener {
                val msg = String.format("%d up, %d down", score.up, score.down)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                true
            }
        } else {
            pointsView.isLongClickable = false
        }

        pointsUnknownView.visible = false
    }


    fun hidePointView() {
        pointsView.visible = false
        pointsUnknownView.visible = false
    }

    fun setPointsUnknown() {
        pointsView.visible = false
        pointsUnknownView.visible = true
    }

    fun setDate(date: Instant) {
        ViewUpdater.replaceText(dateView, date) {
            DurationFormat.timeSincePastPointInTime(context, date, short = true)
        }
    }

    fun setBadgeOpVisible(visible: Boolean) {
        badgeOpView.visible = visible
    }

    fun setOnAnswerClickedListener(onClickListener: View.OnClickListener?) {
        answerView.visible = onClickListener != null
        answerView.setOnClickListener(onClickListener)
    }

    fun setSenderName(name: String, mark: Int) {
        nameView.setUsername(name, mark)
    }

    fun setOnSenderClickedListener(onClickListener: () -> Unit) {
        nameView.setOnClickListener { onClickListener() }
    }

    fun setSingleLine(singleLine: Boolean) {
        orientation = if (singleLine) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
    }
}
