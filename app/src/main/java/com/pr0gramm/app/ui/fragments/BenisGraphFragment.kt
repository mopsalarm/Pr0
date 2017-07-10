package com.pr0gramm.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.androidplot.Plot
import com.androidplot.xy.*
import com.github.salomonbrys.kodein.instance
import com.pr0gramm.app.R
import com.pr0gramm.app.orm.BenisRecord
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.util.AndroidUtility
import rx.Observable
import java.text.*
import java.util.*

class BenisGraphFragment : BaseFragment("BenisGraphFragment") {

    private val plot: XYPlot by bindView(R.id.benis_plot)

    private val userService: UserService by instance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_benis_graph, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val series = BenisGraphDataSource(
                userService.loadBenisRecords(),
                getString(R.string.benis_graph_title))

        val formatter = LineAndPointFormatter(this.context, R.xml.benis_plot_line_point_formatter)
        formatter.pointLabeler = PointLabeler<XYSeries> { s, idx -> "" }

        plot.addSeries(series, formatter)

        PanZoom.attach(plot, PanZoom.Pan.HORIZONTAL, PanZoom.Zoom.STRETCH_HORIZONTAL)

        // add padding to the top because of the action- and statusbar.
        plot.plotPaddingTop = AndroidUtility.getActionBarContentOffset(context).toFloat()

        plot.rangeStepModel = StepModel(StepMode.SUBDIVIDE, 8.0)
        plot.domainStepModel = StepModel(StepMode.SUBDIVIDE, 8.0)

        // default colors match the apps design pretty good.
//        plot.backgroundPaint.color = Color.TRANSPARENT
//        plot.setBackgroundColor(Color.TRANSPARENT)
//        plot.graph.backgroundPaint.color = Color.TRANSPARENT
//        plot.graph.gridBackgroundPaint.color = Color.TRANSPARENT
//        plot.graph.domainSubGridLinePaint.color = Color.TRANSPARENT

        plot.linesPerDomainLabel = 2
        plot.linesPerRangeLabel = 2
        plot.legend.isVisible = false

        plot.setBorderStyle(Plot.BorderStyle.NONE, null, null)

        plot.graph.getLineLabelStyle(XYGraphWidget.Edge.LEFT).format = DecimalFormat("0")

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

    internal inner class BenisGraphDataSource(source: Observable<List<BenisRecord>>,
                                              private val title: String) : XYSeries {

        private var data: List<BenisRecord> = listOf()

        init {
            source.compose(bindToLifecycleAsync()).subscribe {
                handleBenisRecords(it)
            }
        }

        private fun handleBenisRecords(data: List<BenisRecord>) {
            this.data = data

            val minX = data.minBy { it.time }?.time ?: 0L
            val maxX = data.maxBy { it.time }?.time ?: 1L

            val minY = Math.min(data.minBy { it.benis }?.benis ?: 0, 0)
            val maxY = Math.max(data.maxBy { it.benis }?.benis ?: 1, 1)

            val bufferY = 0.2 * (maxY - minY)
            plot.setRangeBoundaries(minY, maxY + bufferY, BoundaryMode.FIXED)
            plot.setDomainBoundaries(minX, maxX, BoundaryMode.FIXED)

            plot.outerLimits.set(minX, maxX, minY - bufferY, maxY + bufferY)

            plot.redraw()
        }


        override fun getTitle(): String = title

        override fun size(): Int = data.size

        override fun getX(idx: Int): Number {
            return data[idx].time
        }

        override fun getY(idx: Int): Number {
            return data[idx].benis
        }
    }
}