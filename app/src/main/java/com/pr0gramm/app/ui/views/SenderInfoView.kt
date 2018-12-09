package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.util.DurationFormat
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.visible

/**
 */
class SenderInfoView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr), View.OnLongClickListener {

    private val nameView: UsernameView
    private val pointsView: TextView
    private val dateView: TextView
    private val answerView: View
    private val badgeOpView: View

    private var score: CommentScore? = null

    init {
        View.inflate(getContext(), R.layout.sender_info, this)
        nameView = find(R.id.username)
        pointsView = find(R.id.points)
        dateView = find(R.id.date)
        answerView = find(R.id.answer)
        badgeOpView = find(R.id.badge_op)

        pointsView.setOnLongClickListener(this)

        setBadgeOpVisible(false)
        setOnAnswerClickedListener(null)
        hidePointView()
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

        this.score = score
    }

    override fun onLongClick(v: View?): Boolean {
        if (v === pointsView) {
            val score = this.score ?: return false
            val msg = String.format("%d up, %d down", score.up, score.down)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            return true
        }

        return false
    }

    fun hidePointView() {
        pointsView.visible = false
        this.score = null
    }

    @SuppressLint("SetTextI18n")
    fun setPointsUnknown() {
        pointsView.text = "\u25CF\u25CF\u25CF"
        pointsView.visible = true
        this.score = null
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
}
