package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.configureNewStyle
import com.pr0gramm.app.util.*

/**
 */
class SenderInfoView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr), View.OnLongClickListener {

    private val nameView: UsernameView
    private val statsView: TextView
    private val answerView: TextView?

    private var date: Instant = Instant.now()
    private var pointsText: String? by observeChange(null) { buildInfoText(apply = true) }

    private var score: CommentScore? = null

    init {
        val useFullLayout = context.obtainStyledAttributes(attrs, R.styleable.SenderInfoView).use { arr ->
            arr.getBoolean(R.styleable.SenderInfoView_siv_reply, false)
        }

        val layout = if (useFullLayout) R.layout.sender_info_answer else R.layout.sender_info
        View.inflate(getContext(), layout, this)

        nameView = find(R.id.username)
        statsView = find(R.id.stats)
        answerView = findOptional(R.id.answer)

        statsView.setOnLongClickListener(this)

        clearOnAnswerClickedListener()
        hidePointView()
    }

    fun setPoints(points: Int) {
        setPoints(points, null)
    }

    fun setPoints(commentScore: CommentScore) {
        setPoints(commentScore.score, commentScore)
    }

    private fun setPoints(points: Int, score: CommentScore?) {
        val id = if (points == 1) R.string.points_one else R.string.points_more
        this.pointsText = context.getString(id, points)
        this.score = score
    }

    private fun buildInfoText(apply: Boolean = false): String {
        val sb = StringBuilder()

        if (pointsText != null) {
            sb.append(pointsText)
            sb.append("   ")
        }

        sb.append(DurationFormat.timeSincePastPointInTime(context, date, short = true))

        val text = sb.toString()

        if (apply) {
            statsView.text = text
        }

        return text
    }

    override fun onLongClick(v: View?): Boolean {
        if (v === statsView) {
            val score = this.score ?: return false
            val msg = String.format("%d Blussies, %d Minus", score.up, score.down)

            Snackbar.make(this, msg, Snackbar.LENGTH_SHORT)
                    .configureNewStyle()
                    .setAction(R.string.okay) {}
                    .show()


            return true
        }

        return false
    }

    fun hidePointView() {
        pointsText = null
    }

    @SuppressLint("SetTextI18n")
    fun setPointsUnknown() {
        pointsText = "\u25CF\u25CF\u25CF"
        score = null
    }

    fun setDate(date: Instant) {
        this.date = date

        ViewUpdater.replaceText(statsView, date) {
            buildInfoText(apply = false)
        }
    }

    fun clearOnAnswerClickedListener() {
        answerView?.isVisible = false
    }

    fun setOnAnswerClickedListener(@StringRes text: Int, onClickListener: View.OnClickListener) {
        answerView?.isVisible = true
        answerView?.setText(text)
        answerView?.setOnClickListener(onClickListener)
    }

    fun setSenderName(name: String, mark: Int, op: Boolean = false) {
        nameView.isVisible = name.isNotBlank()
        nameView.setUsername(name, mark, op)
    }

    fun setOnSenderClickedListener(onClickListener: () -> Unit) {
        nameView.setOnClickListener { onClickListener() }
    }
}
