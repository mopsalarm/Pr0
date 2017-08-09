package com.pr0gramm.app.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.support.v4.view.ViewCompat
import android.support.v7.widget.Toolbar
import android.view.View
import android.widget.TextView
import com.github.salomonbrys.kodein.instance
import com.pr0gramm.app.R
import com.pr0gramm.app.orm.BenisRecord
import com.pr0gramm.app.orm.CachedVote
import com.pr0gramm.app.services.Graph
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.VoteService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.dialogs.ignoreError
import com.pr0gramm.app.ui.views.CircleChartView
import com.pr0gramm.app.ui.views.formatScore
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.getColorCompat
import com.pr0gramm.app.util.visible
import kotterknife.bindView
import java.util.concurrent.TimeUnit

class BenisGraphActivity : BaseAppCompatActivity("BenisGraphFragment") {

    private val userService: UserService by instance()
    private val voteService: VoteService by instance()

    private val benisGraph: View by bindView(R.id.benis_graph)
    private val benisGraphLoading: View by bindView(R.id.benis_graph_loading)
    private val benisGraphEmpty: View by bindView(R.id.benis_graph_empty)

    private val benisChangeDay: TextView by bindView(R.id.stats_change_day)
    private val benisChangeWeek: TextView by bindView(R.id.stats_change_week)
    private val benisChangeMonth: TextView by bindView(R.id.stats_change_month)

    private val usernameView: TextView by bindView(R.id.username)

    private val voteCountUp: TextView by bindView(R.id.stats_up)
    private val voteCountDown: TextView by bindView(R.id.stats_down)
    private val voteCountFavs: TextView by bindView(R.id.stats_fav)

    private val votesByTags: CircleChartView by bindView(R.id.votes_by_tags)
    private val votesByItems: CircleChartView by bindView(R.id.votes_by_items)
    private val votesByComments: CircleChartView by bindView(R.id.votes_by_comments)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.noActionBar)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_benis_graph)

        // setup toolbar as actionbar
        val tb = find <Toolbar>(R.id.toolbar)
        setSupportActionBar(tb)

        // and show back button
        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        voteService.summary.compose(bindToLifecycleAsync()).subscribe {
            handleVoteCounts(it)
        }

        userService.loadBenisRecords()
                .ignoreError()
                .compose(bindToLifecycleAsync())
                .subscribe { handleBenisGraph(it) }

        usernameView.text = (userService.name ?: "xxx").toUpperCase()
    }

    @SuppressLint("SetTextI18n")
    private fun handleVoteCounts(votes: Map<CachedVote.Type, VoteService.Summary>) {
        voteCountUp.text = "UP " + votes.values.sumBy { it.up }
        voteCountDown.text = "DOWN " + votes.values.sumBy { it.down }
        voteCountFavs.text = "FAVS " + votes.values.sumBy { it.fav }

        val zero = VoteService.Summary(0, 0, 0)
        votesByTags.voteSummary = votes[CachedVote.Type.TAG] ?: zero
        votesByItems.voteSummary = votes[CachedVote.Type.ITEM] ?: zero
        votesByComments.voteSummary = votes[CachedVote.Type.COMMENT] ?: zero
    }

    private fun handleBenisGraph(records: List<BenisRecord>) {
        benisGraphLoading.visible = false

        // strip redundant values
        var stripped = records.filterIndexed { idx, value ->
            val benis = value.benis
            idx == 0 || idx == records.size - 1 || records[idx - 1].benis != benis || benis != records[idx + 1].benis
        }

        // dont show if not enough data available
        if (stripped.size < 2 ||
                stripped.all { it.benis == stripped[0].benis } ||
                System.currentTimeMillis() - stripped[0].time < 60 * 1000) {

            benisGraphEmpty.visible = true
            stripped = randomBenisGraph()
        }

        // convert to graph
        val original = Graph(stripped.map { Graph.Point(it.time.toDouble(), it.benis.toDouble()) })

        // sub-sample to only a few points.
        val sampled = original.sampleEquidistant(steps = 16)

        // build the visual
        val dr = GraphDrawable(sampled)
        dr.lineColor = Color.WHITE
        dr.fillColor = 0xa0ffffffL.toInt()
        dr.lineWidth = AndroidUtility.dp(this, 4).toFloat()
        dr.highlightFillColor = getColorCompat(ThemeHelper.primaryColor)

        // add highlight for the a left-ish and a right-ish point.
        dr.highlights.add(GraphDrawable.Highlight(2, formatScore(sampled[2].y.toInt())))
        dr.highlights.add(GraphDrawable.Highlight(13, formatScore(sampled[13].y.toInt())))

        // and show the graph
        ViewCompat.setBackground(benisGraph, dr)

        // calculate the recent changes of benis
        formatChange(original, 1, benisChangeDay)
        formatChange(original, 7, benisChangeWeek)
        formatChange(original, 30, benisChangeMonth)
    }

    private fun randomBenisGraph(): List<BenisRecord> {
        val offset = (Math.random() * 10000).toInt()
        val timeScale = TimeUnit.DAYS.toMillis(3L)

        val values = listOf(0, 100, 75, 150, 90, 60, 130, 160, 90, 70, 60, 130, 170, 210)
        return values.mapIndexed { index, value -> BenisRecord(timeScale * index.toLong(), offset + 10 * value) }
    }

    private fun formatChange(graph: Graph, days: Long, view: TextView) {
        val millis = TimeUnit.DAYS.toMillis(days).toDouble()

        val nowValue = graph.last.y
        val baseValue = graph.valueAt(graph.last.x - millis)

        val absChange = nowValue - baseValue
        val relChange = 100 * absChange / baseValue

        val formatted = (when {
            Math.abs(relChange) < 0.1 -> "%d".format(absChange.toLong())
            Math.abs(relChange) < 9.5 -> "%1.1f%%".format(relChange)
            else -> "%d%%".format(relChange.toLong())
        })

        view.text = formatted
        view.setTextColor(getColorCompat(if (relChange < 0) R.color.stats_down else R.color.stats_up))
    }
}
