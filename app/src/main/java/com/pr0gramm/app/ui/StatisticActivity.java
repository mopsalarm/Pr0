package com.pr0gramm.app.ui;

import android.graphics.Color;
import android.os.Bundle;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IFillFormatter;
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.common.collect.FluentIterable;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.orm.BenisRecord;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;

import static com.pr0gramm.app.services.ThemeHelper.theme;


public class StatisticActivity extends BaseAppCompatActivity {

    @Inject
    UserService userService;

    @BindView(R.id.chart1)
    LineChart benisChart;

    protected void injectComponent(ActivityComponent component) {
        component.inject(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(theme().basic);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistic);

        benisChart.getDescription().setEnabled(false);

        benisChart.setTouchEnabled(true);
        benisChart.setDragEnabled(true);
        benisChart.setScaleEnabled(true);
        benisChart.setPinchZoom(true);

        benisChart.setDrawGridBackground(false);
        benisChart.setMaxHighlightDistance(300);


        XAxis x = benisChart.getXAxis();
        x.setEnabled(true);
        //x.setLabelCount(6, false);
        x.setDrawLabels(false);
        x.setTextColor(Color.WHITE);
        x.setPosition(XAxis.XAxisPosition.BOTTOM_INSIDE);
        x.setAxisLineColor(Color.WHITE);
        x.setDrawGridLines(false);

        YAxis y = benisChart.getAxisLeft();
        y.setLabelCount(6, false);
        y.setTextColor(Color.WHITE);
        y.setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART);
        y.setDrawGridLines(false);
        y.setAxisLineColor(Color.WHITE);

        benisChart.getAxisRight().setEnabled(false);

        LineDataSet set = new LineDataSet(null, null);


        set.setColor(Color.WHITE);
        set.setLineWidth(1.8f);
        set.setDrawHorizontalHighlightIndicator(false);

        set.setDrawCircles(false);
        set.setCircleRadius(3f);
        set.setCircleColor(Color.WHITE);

        set.setDrawFilled(false);

        set.setValueTextSize(6f);

        set.setMode(LineDataSet.Mode.LINEAR);



        for (BenisRecord record : userService.loadFullBenisHistory()) {
            set.addEntry(new Entry((float) record.time, (float) record.benis));
        }
        benisChart.getLegend().setEnabled(false);

        benisChart.setData(new LineData(set));


    }

}
