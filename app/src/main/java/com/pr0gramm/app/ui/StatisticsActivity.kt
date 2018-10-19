package com.pr0gramm.app.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.orm.BenisRecord
import com.pr0gramm.app.orm.CachedVote
import com.pr0gramm.app.services.*
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.dialogs.ignoreError
import com.pr0gramm.app.ui.views.CircleChartView
import com.pr0gramm.app.ui.views.TimeRangeSelectorView
import com.pr0gramm.app.ui.views.formatScore
import com.pr0gramm.app.util.*
import kotterknife.bindView
import org.kodein.di.erased.instance
import rx.Observable
import java.util.concurrent.TimeUnit

class StatisticsActivity : BaseAppCompatActivity("StatisticsActivity") {

    private val userService: UserService by instance()
    private val voteService: VoteService by instance()
    private val statsService: StatisticsService by instance()

    private val benisGraph: View by bindView(R.id.benis_graph)
    private val benisGraphLoading: View by bindView(R.id.benis_graph_loading)
    private val benisGraphEmpty: View by bindView(R.id.benis_graph_empty)
    private val benisGraphTimeSelector: TimeRangeSelectorView by bindView(R.id.graph_time_selector)

    private val benisChangeDay: TextView by bindView(R.id.stats_change_day)
    private val benisChangeWeek: TextView by bindView(R.id.stats_change_week)
    private val benisChangeMonth: TextView by bindView(R.id.stats_change_month)

    private val voteCountUp: TextView by bindView(R.id.stats_up)
    private val voteCountDown: TextView by bindView(R.id.stats_down)
    private val voteCountFavs: TextView by bindView(R.id.stats_fav)

    private val votesByTags: CircleChartView by bindView(R.id.votes_by_tags)
    private val votesByItems: CircleChartView by bindView(R.id.votes_by_items)
    private val votesByComments: CircleChartView by bindView(R.id.votes_by_comments)

    private val typesOfUpload: CircleChartView by bindView(R.id.types_uploads)
    private val typesByFavorites: CircleChartView by bindView(R.id.types_favorites)

    private var benisValues: List<BenisRecord> by observeChange(listOf()) {
        redrawBenisGraph()
        updateTimeRange()
    }

    private var benisTimeRangeStart: Long by observeChange(0L) { redrawBenisGraph() }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.noActionBar)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_statistics)

        // setup toolbar as actionbar
        val tb = find<Toolbar>(R.id.toolbar)
        setSupportActionBar(tb)

        // and show back button
        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        benisGraphTimeSelector.selectedTimeRange.bindToLifecycle().subscribe { millis ->
            benisTimeRangeStart = System.currentTimeMillis() - millis
        }

        voteService.summary.ignoreError().bindToLifecycleAsync().subscribe {
            handleVoteCounts(it)
        }

        userService.loadBenisRecords()
                .ignoreError()
                .delay(200, TimeUnit.MILLISECONDS)
                .bindToLifecycleAsync()
                .subscribe { benisValues = it.records }

        userService.name?.let { username ->
            showContentTypesOf(typesByFavorites, statsService.statsForFavorites(username))
            showContentTypesOf(typesOfUpload, statsService.statsForUploads(username))
        }
    }

    private fun showContentTypesOf(view: CircleChartView, stats: Observable<StatisticsService.Stats>) {
        stats.bindToLifecycleAsync().subscribeWithErrorHandling {
            showContentTypes(view, it)
        }
    }

    private fun showContentTypes(view: CircleChartView, stats: StatisticsService.Stats) {
        val counts = stats.counts
        val sfw = counts[ContentType.SFW] ?: 0
        val nsfw = (counts[ContentType.NSFW] ?: 0) + (counts[ContentType.NSFP] ?: 0)
        val nsfl = counts[ContentType.NSFL] ?: 0

        val values = listOf(
                CircleChartView.Value(sfw, getColorCompat(R.color.type_sfw)),
                CircleChartView.Value(nsfw, getColorCompat(R.color.type_nsfw)),
                CircleChartView.Value(nsfl, getColorCompat(R.color.type_nsfl)))


        view.chartValues = values
    }

    @SuppressLint("SetTextI18n")
    private fun handleVoteCounts(votes: Map<CachedVote.Type, VoteService.Summary>) {
        voteCountUp.text = "UP " + votes.values.sumBy { it.up }
        voteCountDown.text = "DOWN " + votes.values.sumBy { it.down }
        voteCountFavs.text = "FAVS " + votes.values.sumBy { it.fav }

        votesByTags.chartValues = toChartValues(votes[CachedVote.Type.TAG])
        votesByItems.chartValues = toChartValues(votes[CachedVote.Type.ITEM])
        votesByComments.chartValues = toChartValues(votes[CachedVote.Type.COMMENT])
    }

    private fun toChartValues(summary: VoteService.Summary?): List<CircleChartView.Value> {
        summary ?: return listOf()

        return listOf(
                CircleChartView.Value(summary.up, getColorCompat(R.color.stats_up)),
                CircleChartView.Value(-summary.down, getColorCompat(R.color.stats_down)),
                CircleChartView.Value(summary.fav, getColorCompat(R.color.stats_fav)))
    }

    private fun updateTimeRange() {
        if (benisValues.size > 2) {
            val min = benisValues.minBy { it.time }!!.time
            val max = benisValues.maxBy { it.time }!!.time

            benisGraphTimeSelector.maxRangeInMillis = (max - min)
        }
    }

    private fun redrawBenisGraph() {
        benisGraphLoading.visible = false
        benisGraphTimeSelector.visible = true

        var records = optimizeValuesBy(benisValues) { it.benis.toDouble() }

        // dont show if not enough data available
        if (records.size < 2 ||
                records.all { it.benis == records[0].benis } ||
                System.currentTimeMillis() - records[0].time < 60 * 1000) {

            benisGraphEmpty.visible = true
            benisGraphTimeSelector.visible = false
            records = randomBenisGraph()
        }

        // convert to graph
        val original = Graph(records.map { Graph.Point(it.time.toDouble(), it.benis.toDouble()) })

        // sub-sample to only a few points.
        val startValue = benisTimeRangeStart.toDouble().coerceAtLeast(original.first.x)
        val sampled = original.sampleEquidistant(steps = 16, start = startValue)

        // build the visual
        val dr = GraphDrawable(sampled).apply {
            lineColor = Color.WHITE
            fillColor = 0xa0ffffffL.toInt()
            lineWidth = dip2px(4f)
            highlightFillColor = getColorCompat(ThemeHelper.primaryColorDark)
        }

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

        view.text = formatScore(absChange.toInt())
        view.setTextColor(getColorCompat(if (absChange < 0) R.color.stats_down else R.color.stats_up))
    }
}
