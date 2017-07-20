package com.pr0gramm.app.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import com.androidplot.Plot
import com.androidplot.xy.*
import com.github.salomonbrys.kodein.instance
import com.pr0gramm.app.R
import com.pr0gramm.app.orm.BenisRecord
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.getColorCompat
import kotterknife.bindView
import java.text.FieldPosition
import java.text.Format
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class BenisGraphActivity : BaseAppCompatActivity("BenisGraphFragment") {

    private val plot: XYPlot by bindView(R.id.benis_plot)
    private val userService: UserService by instance()
    private var series: BenisSeries = BenisSeries(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_benis_graph)

        setupZoomButtons()
        loadBenisGraphSeries()

        PanZoom.attach(plot, PanZoom.Pan.HORIZONTAL, PanZoom.Zoom.STRETCH_HORIZONTAL)

        plot.rangeStepModel = StepModel(StepMode.SUBDIVIDE, 8.0)
        plot.domainStepModel = StepModel(StepMode.SUBDIVIDE, 4.0)

        // default colors match the apps design pretty good.
//        plot.backgroundPaint.color = Color.TRANSPARENT
//        plot.setBackgroundColor(Color.TRANSPARENT)
//        plot.graph.backgroundPaint.color = Color.TRANSPARENT
//        plot.graph.gridBackgroundPaint.color = Color.TRANSPARENT
//        plot.graph.domainSubGridLinePaint.color = Color.TRANSPARENT

        plot.legend.isVisible = false

        plot.setBorderStyle(Plot.BorderStyle.NONE, null, null)

        plot.graph.backgroundPaint.color = Color.TRANSPARENT
        plot.graph.gridBackgroundPaint.color = Color.TRANSPARENT

        plot.graph.getLineLabelStyle(XYGraphWidget.Edge.LEFT).format = ShortNumberFormat()

        plot.graph.getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).format = object : Format() {
            val dateFormat = SimpleDateFormat("dd.MM.yy")

            override fun format(obj: Any, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer {
                val ts = (obj as Number).toLong()
                return dateFormat.format(Date(ts), toAppendTo, pos)
            }

            override fun parseObject(source: String, pos: ParsePosition): Any? {
                return null
            }
        }
    }

    private fun setupZoomButtons() {
        val buttons = mapOf(
                R.id.btn_forever to Long.MAX_VALUE,
                R.id.btn_month to TimeUnit.DAYS.toMillis(30),
                R.id.btn_week to TimeUnit.DAYS.toMillis(7),
                R.id.btn_day to TimeUnit.DAYS.toMillis(1))

        buttons.forEach { (id, millis) ->
            val button: Button = find(id)
            button.setOnClickListener {
                val minX = (series.maxX - millis).coerceAtLeast(series.minX)
                plot.setDomainBoundaries(minX, series.maxX, BoundaryMode.FIXED)
                plot.redraw()
            }
        }
    }

    /**
     * Loads the benis graph in background.
     */
    private fun loadBenisGraphSeries() {
        userService.loadBenisRecords().compose(bindToLifecycleAsync()).subscribe { data ->
            logger.info("Loaded {} benis record items.", data.size)

            // strip redundant values
            val stripped = data.filterIndexed { idx, value ->
                val benis = value.benis
                idx == 0 || idx == data.size - 1 || data[idx - 1].benis != benis || benis != data[idx + 1].benis
            }

            logger.info("Stripped values down to {} items", stripped.size)

            val series = BenisSeries(stripped)
            this.series = series

            plot.setDomainBoundaries(series.minX, series.maxX, BoundaryMode.FIXED)

            val bufferY = 0.2 * (series.maxY - series.minY)
            plot.setRangeBoundaries(series.minY - bufferY, series.maxY + bufferY, BoundaryMode.FIXED)

            plot.outerLimits.set(series.minX, series.maxX, series.minY - bufferY, series.maxY + bufferY)

            // add the series
            val formatter = LineAndPointFormatter(this, R.xml.benis_plot_line_point_formatter)
            formatter.pointLabelFormatter = null
            formatter.vertexPaint = null
            formatter.linePaint.color = getColorCompat(ThemeHelper.accentColor)
            plot.addSeries(series, formatter)

            plot.redraw()
        }
    }

    internal inner class BenisSeries(private val data: List<BenisRecord>) : FastXYSeries, OrderedXYSeries {
        val minX = data.minBy { it.time }?.time ?: 0L
        val maxX = data.maxBy { it.time }?.time ?: 1L

        val minY = data.minBy { it.benis }?.benis ?: 0
        val maxY = data.maxBy { it.benis }?.benis ?: 0

        override fun minMax(): RectRegion = RectRegion(minX, maxX, minY, maxY)

        override fun getTitle(): String = "benis"

        override fun size(): Int = data.size

        override fun getX(idx: Int): Number {
            return data[idx].time
        }

        override fun getY(idx: Int): Number {
            return data[idx].benis
        }

        override fun getXOrder(): OrderedXYSeries.XOrder = OrderedXYSeries.XOrder.ASCENDING
    }

    /**
     * Formats number using "k" or "m" suffix.
     *
     * E.g:
     *   1000 -> 1.00k
     *   25800 -> 25.8k
     *   343000 -> 343k
     */
    class ShortNumberFormat : Format() {
        override fun format(obj: Any, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer {
            val value = (obj as Number).toLong()
            return toAppendTo.append(when {
                value >= 1000_000 -> "%1.2fm".format(value / 1000000f)

                value >= 100_000 -> "%1fk".format(value / 1000f)
                value >= 10_000 -> "%1.1fk".format(value / 1000f)
                value >= 1_000 -> "%1.2fk".format(value / 1000f)
                else -> value.toString()
            })
        }

        override fun parseObject(source: String?, pos: ParsePosition?): Any {
            throw UnsupportedOperationException()
        }
    }
}
