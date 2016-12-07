package com.pr0gramm.app.ui.search;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.jakewharton.rxbinding.widget.RxSeekBar;
import com.pr0gramm.app.R;
import com.trello.rxlifecycle.android.RxLifecycleAndroid;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Observable;

/**
 */
public class SearchViewScore extends LinearLayout implements SearchView {
    private final Observable<Integer> values;

    @BindView(R.id.search_score_slider)
    SeekBar scoreSlider;

    @BindView(R.id.search_score_text)
    TextView scoreText;

    public SearchViewScore(Context context) {
        super(context);

        // inflate and bind!
        inflate(context, R.layout.search_view_score, this);
        ButterKnife.bind(this);

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        values = RxSeekBar.changes(scoreSlider)
                .map(value -> (Math.round(value / 100.f)) * 100)
                .share();

        values.compose(RxLifecycleAndroid.bindView(scoreSlider))
                .map(SearchViewScore::valueToString)
                .subscribe(scoreText::setText);

        scoreSlider.setMax(9500);
        scoreSlider.setKeyProgressIncrement(100);
    }

    @Override
    public View view() {
        return this;
    }

    @Override
    public void focus() {
        scoreSlider.requestFocus();
    }

    @Override
    public Observable<Boolean> valid() {
        return Observable.just(true);
    }

    @Override
    public Observable<String> queryString() {
        return values.map(value -> "s:" + valueToString(value));
    }

    private static String valueToString(Integer value) {
        return value == 0 ? "shit" : String.valueOf(value);
    }
}
