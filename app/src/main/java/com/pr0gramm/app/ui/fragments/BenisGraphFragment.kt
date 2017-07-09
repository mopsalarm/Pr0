package com.pr0gramm.app.ui.fragments

/**
 * Created by chr0sey on 29.06.2017.
 */

import android.graphics.Color
import android.os.Bundle
import android.support.annotation.MainThread
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
import com.pr0gramm.app.util.BackgroundScheduler
import java.text.*
import java.util.*
import rx.Observable

class BenisGraphFragment : BaseFragment("BenisGraphFragment") {

    private val plot: XYPlot by bindView(R.id.benis_plot)
    private val userService: UserService by instance()
    internal lateinit var data: BenisGraphDataSource
    internal var firstOfDays: MutableMap<String, Long> = mutableMapOf()
    private val dateFormat = SimpleDateFormat("dd.MM.yy")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_benis_graph, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        data = BenisGraphDataSource()
        val serie = BenisSeries(data,getString(R.string.benis_graph_title))
        val formatter = LineAndPointFormatter(this.context,R.xml.benis_plot_line_point_formatter)
        formatter.pointLabeler = PointLabeler<XYSeries> { p0, p1 -> if (p1 % 5 == 0) p0.getY(p1).toString() else "" }
        plot.addSeries(serie, formatter)

        PanZoom.attach(plot,PanZoom.Pan.HORIZONTAL,PanZoom.Zoom.STRETCH_HORIZONTAL)

        plot.rangeStepModel = StepModel(StepMode.INCREMENT_BY_FIT, 1.0)
        plot.backgroundPaint.color = Color.TRANSPARENT
        plot.setBackgroundColor(Color.TRANSPARENT)
        plot.graph.backgroundPaint.color = Color.TRANSPARENT
        plot.graph.gridBackgroundPaint.color = Color.TRANSPARENT

        plot.graph.domainSubGridLinePaint.color = Color.TRANSPARENT
        plot.graph.linesPerDomainLabel
        plot.linesPerDomainLabel = 2
        plot.linesPerRangeLabel = 2
        plot.legend.isVisible = false
        plot.setBorderStyle(Plot.BorderStyle.NONE, null, null)
        plot.graph.getLineLabelStyle(XYGraphWidget.Edge.LEFT)
                .format = DecimalFormat("0")

        plot.graph.getLineLabelStyle(XYGraphWidget.Edge.BOTTOM)
                .format = object : Format() {

            private val timeFormat = SimpleDateFormat("HH:mm")
            override fun format(obj: Any, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer {
                val ts = Math.round((obj as Number).toDouble())
                val date = Date(ts)
                if (firstOfDays.containsValue(ts)){
                    return dateFormat.format(date,toAppendTo,pos)
                }
                return timeFormat.format(date, toAppendTo, pos)
            }

            override fun parseObject(source: String, pos: ParsePosition): Any? {
                return null
            }
        }
    }

    internal inner class BenisGraphDataSource {

        private var data: MutableList<BenisRecord> = mutableListOf()
        private var minX: Long = Long.MAX_VALUE
        private var maxX: Long = Long.MIN_VALUE
        private var minY: Int = 0
        private var maxY: Int = 0

        init {
            Observable.fromCallable{ userService.loadBenis()}
                    .subscribeOn(BackgroundScheduler.instance())
                    .subscribe{
                for (br in it){
                    data.add(br)
                    if(minX > br.time) minX = br.time
                    if(maxX < br.time) maxX = br.time
                    if(minY > br.benis) minY = br.benis
                    if(maxY < br.benis) maxY = br.benis
                    var date = dateFormat.format(Date(br.time))
                    firstOfDays[date] = Math.min(br.time, firstOfDays.getOrDefault(date,Long.MAX_VALUE))

                    plot.outerLimits.set(
                            minX,
                            maxX,
                            minY,
                            maxY*1.2
                    )
                    plot.setRangeBoundaries(
                            minY,
                            maxY*1.2,
                            BoundaryMode.FIXED
                    )
                    plot.setDomainBoundaries(
                            minX,
                            maxX,
                            BoundaryMode.FIXED
                    )
                    plot.redraw()

                }

            }
        }

        fun  getItemCount(): Int {
            return data.size
        }

        fun  getX(p0: Int): Number {
            return data[p0].time
        }
        fun getY(p0: Int): Number {
            return data[p0].benis
        }
    }

    internal inner class BenisSeries(private val datasource: BenisGraphDataSource, private val title: String) : XYSeries{
        override fun getTitle(): String {
            return getString(R.string.benis_graph_title)
        }

        override fun size(): Int {
            return datasource.getItemCount()
        }

        override fun getX(p0: Int): Number {
            return datasource.getX(p0)
        }

        override fun getY(p0: Int): Number {
            return datasource.getY(p0)
        }
    }

}