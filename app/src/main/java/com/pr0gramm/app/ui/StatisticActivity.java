package com.pr0gramm.app.ui;

import android.graphics.Color;
import android.os.Bundle;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IFillFormatter;
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;

import javax.inject.Inject;

import butterknife.BindView;
import rx.functions.Actions;

;


public class StatisticActivity extends BaseAppCompatActivity {

    @Inject
    UserService userService;

    @BindView(R.id.chart1)
    LineChart mChart;

    @Override
    protected void injectComponent(ActivityComponent appComponent) {
        appComponent.inject(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeHelper.theme().basic);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistic);

        mChart.setViewPortOffsets(0, 0, 0, 0);

        mChart.getDescription().setEnabled(false);
        mChart.setTouchEnabled(true);
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);

        mChart.setPinchZoom(false);
        mChart.setDrawGridBackground(false);
        mChart.setMaxHighlightDistance(300);

        XAxis x = mChart.getXAxis();
        x.setEnabled(false);

        YAxis y = mChart.getAxisLeft();
        y.setLabelCount(6, false);
        y.setTextColor(Color.WHITE);
        y.setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART);
        y.setDrawGridLines(false);
        y.setAxisLineColor(Color.WHITE);

        mChart.getAxisRight().setEnabled(false);

       userService.loginState().subscribe(s -> updateGraph(s));
    }

    @Override
    public void onResume() {
        super.onResume();

        userService.loginState()
                .compose(bindToLifecycleAsync())
                .subscribe(this::onLoginStateChanged, Actions.empty());
    }

    private void onLoginStateChanged(UserService.LoginState state) {
        //updateGraph(state);
    }

    private void updateGraph(UserService.LoginState state) {
        if (state.authorized()) {
            LineDataSet set = null;
            //set = state.benisFullHistory();
            if (set == null) {
                return;
            }
            set.setMode(LineDataSet.Mode.LINEAR);
            set.setCubicIntensity(0.2f);
            set.setDrawCircles(false);
            set.setLineWidth(1.8f);
            set.setCircleRadius(4f);
            set.setCircleColor(Color.WHITE);
            set.setHighLightColor(Color.rgb(244, 117, 117));
            set.setColor(Color.WHITE);
            set.setFillColor(Color.WHITE);
            set.setFillAlpha(100);
            set.setDrawHorizontalHighlightIndicator(false);
            set.setFillFormatter(new IFillFormatter() {
                @Override
                public float getFillLinePosition(ILineDataSet dataSet, LineDataProvider dataProvider) {
                    return -10;
                }
            });
            LineData data = new LineData(set);
            data.setValueTextSize(9f);
            data.setDrawValues(false);
            mChart.setData(data);
        } else {
            mChart.clear();
        }
    }
}
