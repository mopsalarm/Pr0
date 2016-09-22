package com.pr0gramm.app.ui.search;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.RadioGroup;

import com.jakewharton.rxbinding.widget.RxRadioGroup;
import com.pr0gramm.app.R;

import butterknife.ButterKnife;
import rx.Observable;

/**
 */
public class SearchViewQuality extends RadioGroup implements SearchView {
    private final Observable<String> values;

    public SearchViewQuality(Context context) {
        super(context);

        // inflate and bind!
        inflate(context, R.layout.search_view_quality, this);
        ButterKnife.bind(this);

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        values = RxRadioGroup.checkedChanges(this).map(id -> {
            switch (id) {
                case R.id.search_q_sd:
                    return "q:sd";
                case R.id.search_q_4k:
                    return "q:4k";
                case R.id.search_q_potato:
                    return "q:kartoffel";
                case R.id.search_q_hd:
                default:
                    return "q:hd";
            }
        }).share();

        // check sfw by default
        check(R.id.search_sfw);
    }

    @Override
    public View view() {
        return this;
    }

    @Override
    public Observable<Boolean> valid() {
        return Observable.just(true);
    }

    @Override
    public Observable<String> queryString() {
        return values;
    }

    @Override
    public void focus() {
        requestFocus();
    }
}
