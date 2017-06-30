package com.pr0gramm.app.ui.fragments

/**
 * Created by chr0sey on 29.06.2017.
 */

import android.app.ProgressDialog
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Color.*
import android.graphics.DashPathEffect
import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.androidplot.Plot
import com.androidplot.util.PixelUtils;
import com.androidplot.xy.*
import com.github.salomonbrys.kodein.instance
import com.pr0gramm.app.R
import com.pr0gramm.app.R.id.progress
import com.pr0gramm.app.Settings
import com.pr0gramm.app.orm.BenisRecord
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.back.BackAwareFragment
import com.pr0gramm.app.ui.base.BaseFragment
import java.text.*
import java.util.*
import kotlin.collections.HashSet

class BenisGraphFragment : BaseFragment("BenisGraphFragment"), BackAwareFragment {


    private val settings = Settings.get()
    private val plot: XYPlot by bindView(R.id.benis_plot)
    private val userService: UserService by instance()
    private val dateFormat = SimpleDateFormat("dd.MM.yy")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_benis_graph, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val data = userService.loadBenis().map { rec -> BenisRecord(rec.time, rec.benis) }

        val minBenis = Math.min(0, data.minBy { rec -> rec.benis }!!.benis)
        val maxBenis = Math.ceil(data.maxBy { rec -> rec.benis }!!.benis*1.5).toInt()
        val startTime = (data.minBy { rec -> rec.time })!!.time
        val endTime = (data.maxBy { rec -> rec.time })!!.time

        PanZoom.attach(plot,PanZoom.Pan.HORIZONTAL,PanZoom.Zoom.STRETCH_HORIZONTAL)
        plot.outerLimits.set(startTime-2000,endTime+2000,minBenis,maxBenis)

        val points = SimpleXYSeries(
                data.map { record -> record.time },
                data.map { record -> record.benis},
                "Dein Benis"
        )
        plot.graph.paddingLeft = 5f
        plot.setBorderStyle(Plot.BorderStyle.SQUARE,null,null)

        val formatter = LineAndPointFormatter(this.context, R.xml.benis_plot_line_point_formatter)

        //formatter.interpolationParams = CatmullRomInterpolator.Params(5,CatmullRomInterpolator.Type.Centripetal)
        plot.addSeries(points, formatter)

        val df = SimpleDateFormat("dd.MM.yy")
        val hash = HashSet<String>()
        hash.addAll(data.map{rec -> df.format(rec.time)})
        plot.domainStepModel = StepModel(StepMode.SUBDIVIDE,hash.size.toDouble())
        plot.rangeStepModel = StepModel(StepMode.INCREMENT_BY_FIT, 5.0)
        /*
        val incDomain = arrayOf(1.0,7.0,30.0,356.0,1000.0).toDoubleArray()

        val incRange = arrayOf(1.0,5.0,10.0,50.0,100.0,500.0,1000.0,10000.0).toDoubleArray()

        plot.domainStepModel = StepModelFit(plot.bounds.getxRegion(),incDomain,5.0)
        plot.rangeStepModel = StepModelFit(plot.bounds.getyRegion(),incRange,5.0)

        val incDomain = arrayOf(1.0,12.0,24.0,168.0,720.0).toDoubleArray()
        plot.domainStepModel = StepModelFit(plot.bounds.getxRegion(),incDomain,5.0)
        */
        plot.setRangeBoundaries(minBenis,maxBenis,BoundaryMode.FIXED)

        plot.backgroundPaint.color = TRANSPARENT
        plot.setBackgroundColor(Color.TRANSPARENT)
        plot.graph.backgroundPaint.color = TRANSPARENT
        plot.graph.gridBackgroundPaint.color = TRANSPARENT
        plot.linesPerDomainLabel = 1
        plot.linesPerRangeLabel = 1
        plot.legend.isVisible = false

        plot.graph.getLineLabelStyle(XYGraphWidget.Edge.LEFT)
                .format = DecimalFormat("0")

        plot.graph.getLineLabelStyle(XYGraphWidget.Edge.BOTTOM)
                .format = object : Format() {

            override fun format(obj: Any, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer {
                val ts = Math.round((obj as Number).toDouble())
                val date = Date(ts)
                return dateFormat.format(date, toAppendTo, pos)
            }

            override fun parseObject(source: String, pos: ParsePosition): Any? {
                return null
            }
        }


    }

    override fun onBackButton(): Boolean {
        return false;
    }

}