package com.pr0gramm.app.ui.fragments

/**
 * Created by chr0sey on 29.06.2017.
 */

import android.graphics.Color
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.androidplot.Plot
import com.androidplot.util.Redrawer
import com.androidplot.xy.*
import com.github.salomonbrys.kodein.instance
import com.pr0gramm.app.R
import com.pr0gramm.app.orm.BenisRecord
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.BaseFragment
import java.text.*
import java.util.*
import kotlin.collections.HashMap

class BenisGraphFragment : BaseFragment("BenisGraphFragment") {

    private inner class MyPlotUpdater(internal var plot: XYPlot):Observer{

        override fun update(o: Observable?, arg: Any?) {
            // TODO ("Check dataTypes of arg")
            if (arg is Array<*>){
                plot.outerLimits.set(arg[0] as Long ,arg[1] as Long,arg[2] as Int,arg[3] as Int)
                plot.setRangeBoundaries(arg[2] as Int, arg[3] as Int,BoundaryMode.FIXED)
            }
            plot.redraw()
        }
    }

    private val plot: XYPlot by bindView(R.id.benis_plot)
    private val userService: UserService by instance()
    private val dateFormat = SimpleDateFormat("dd.MM.yy")
    private val timeFormat = SimpleDateFormat("HH:mm")
    private var plotUpdater: MyPlotUpdater? = null
    internal lateinit var data: BenisGraphDataSource
    private var  myThread: Thread? = null
    internal var firstOfDays: MutableMap<String, Long> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_benis_graph, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        plotUpdater = MyPlotUpdater(plot)
        data = BenisGraphDataSource()
        val serie = BenisSeries(data,getString(R.string.benis_graph_title))
        val formatter = LineAndPointFormatter(this.context,R.xml.benis_plot_line_point_formatter)
        formatter.pointLabeler = PointLabeler<XYSeries> { p0, p1 -> if (p1 % 5 == 0) p0.getY(p1).toString() else "" }
        plot.addSeries(serie, formatter)

        data.addObserver(plotUpdater!!)

        PanZoom.attach(plot,PanZoom.Pan.HORIZONTAL,PanZoom.Zoom.STRETCH_HORIZONTAL)

        plot.rangeStepModel = StepModel(StepMode.INCREMENT_BY_FIT, 1.0)
        plot.backgroundPaint.color = Color.TRANSPARENT
        plot.setBackgroundColor(Color.TRANSPARENT)
        plot.graph.backgroundPaint.color = Color.TRANSPARENT
        plot.graph.gridBackgroundPaint.color = Color.TRANSPARENT

        plot.linesPerDomainLabel = 3
        plot.linesPerRangeLabel = 1
        plot.legend.isVisible = false
        plot.graph.getLineLabelStyle(XYGraphWidget.Edge.LEFT)
                .format = DecimalFormat("0")

        plot.graph.getLineLabelStyle(XYGraphWidget.Edge.BOTTOM)
                .format = object : Format() {

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

    override fun onResume() {
        myThread = Thread(data)
        myThread!!.start()
        super.onResume()
    }

    override fun onPause() {
        data.stopThread()
        super.onPause()
    }

    internal inner class BenisGraphDataSource : Runnable {
        internal inner class MyObservable : Observable() {
            override fun notifyObservers() {
                setChanged()
                super.notifyObservers(0)
            }
            override fun notifyObservers(args: Any?) {
                setChanged()
                super.notifyObservers(args)
            }
        }

        private var notifier: MyObservable? = null
        private var data: MutableList<BenisRecord>
        private var minX: Long = Long.MAX_VALUE
        private var maxX: Long = Long.MIN_VALUE
        private var minY: Int = 0
        private var maxY: Int = 0

        init {
            notifier = MyObservable()
            data = mutableListOf()
        }

        fun stopThread() {
        }

        override fun run() {
            try {
                userService.loadBenis().subscribe{
                    data.add(it)
                    minX = if(minX > it.time) it.time -1000 else minX
                    maxX = if(maxX < it.time) it.time +1000 else maxX
                    minY = if(minY > it.benis) it.benis else minY
                    maxY = if(maxY < it.benis) it.benis else maxY
                    var date = dateFormat.format(Date(it.time))
                    firstOfDays.set(date, Math.min(it.time, firstOfDays.getOrDefault(date,Long.MAX_VALUE)))


                    notifier!!.notifyObservers(arrayOf(minX,maxX,Math.floor(minY * 1.1).toInt(),Math.ceil(maxY * 1.1).toInt()))
                }
                notifier!!.notifyObservers()
            } catch(e: InterruptedException){
                e.printStackTrace()
            }
        }

        fun addObserver(observer: Observer){
            notifier!!.addObserver(observer)
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