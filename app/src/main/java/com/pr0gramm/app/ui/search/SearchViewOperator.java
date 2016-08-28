package com.pr0gramm.app.ui.search;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.pr0gramm.app.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnItemSelected;
import rx.Observable;
import rx.subjects.BehaviorSubject;

import static com.google.common.collect.Iterables.transform;
import static com.pr0gramm.app.util.AndroidUtility.dp;
import static com.trello.rxlifecycle.android.RxLifecycleAndroid.bindView;
import static java.util.Arrays.asList;

/**
 */
class SearchViewOperator extends FrameLayout implements SearchView {
    private final BehaviorSubject<Operator> operator = BehaviorSubject.create();
    private final BehaviorSubject<List<SearchView>> subSearches = BehaviorSubject.create(Collections.emptyList());

    @BindView(R.id.sub_searches_container)
    ViewGroup subSearchesContainer;

    @BindView(R.id.operator_spinner)
    Spinner operatorSpinner;

    @BindView(R.id.row_remove)
    ImageButton removeRowButton;

    SearchViewOperator(Context context) {
        super(context);

        View.inflate(getContext(), R.layout.search_view_operator, this);
        ButterKnife.bind(this);

        operatorSpinner.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item, android.R.id.text1,
                Operator.values()));

        subSearches
                .compose(bindView(this))
                .map(views -> views.size() > 1)
                .subscribe(valid -> {
                    removeRowButton.setEnabled(valid);
                    removeRowButton.setAlpha(valid ? 1 : 0.5f);
                });

        // add two default rows
        onAddRowClicked();
        onAddRowClicked();
    }

    @OnClick(R.id.row_add)
    public void onAddRowClicked() {
        SearchConfigView view = new SearchConfigView(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        params.bottomMargin = dp(getContext(), 8);
        params.leftMargin = dp(getContext(), 8);
        params.rightMargin = dp(getContext(), 8);
        subSearchesContainer.addView(view, params);

        // focus the new one
        view.focus();

        // publish changes
        subSearches.onNext(listSearchViews());
    }

    @OnClick(R.id.row_remove)
    public void onRemoveRowClicked() {
        int viewCount = subSearchesContainer.getChildCount();
        if (viewCount > 1) {
            // remove the last view
            subSearchesContainer.removeViews(viewCount - 1, 1);

            // focus the now last view
            List<SearchView> searchViews = listSearchViews();
            if (searchViews.size() > 0) {
                searchViews.get(searchViews.size() - 1).focus();
            }

            // and publish changes
            subSearches.onNext(searchViews);
        }
    }


    @OnItemSelected(R.id.operator_spinner)
    public void onOperatorSelected(int idx) {
        this.operator.onNext(Operator.values()[idx]);
    }

    private List<SearchView> listSearchViews() {
        List<SearchView> searchViews = new ArrayList<>();
        for (int idx = 0; idx < subSearchesContainer.getChildCount(); idx++) {
            View view = subSearchesContainer.getChildAt(idx);
            if (view instanceof SearchView) {
                searchViews.add((SearchView) view);
            }
        }

        return searchViews;
    }

    @Override
    public View view() {
        return this;
    }

    @Override
    public void focus() {
        List<SearchView> subViews = listSearchViews();
        if (subViews.size() > 0)
            subViews.get(0).focus();
    }


    @Override
    public Observable<Boolean> valid() {
        return subSearches.switchMap(searches -> Observable.combineLatest(
                transform(searches, SearchView::valid),
                values -> Iterables.all(asList(values), val -> (boolean) val)
        ));
    }

    @Override
    public Observable<String> queryString() {
        return subSearches.switchMap(searches -> {
            ImmutableList<Observable<Object>> observables = ImmutableList.<Observable<Object>>builder()
                    .add(operator.cast(Object.class))
                    .addAll(transform(searches, v -> v.queryString().cast(Object.class)))
                    .build();

            return Observable.combineLatest(observables, values -> {
                Operator operator = (Operator) values[0];
                return FluentIterable.of(values).skip(1)
                        .filter(String.class).transform(QueryUtil::escapeQuery)
                        .join(Joiner.on(" " + operator.op + " "));
            });
        });
    }

    enum Operator {
        OR("|", "oder (kadse oder kefer)"),
        AND("&", "und (0815 und porn)"),
        WITHOUT("-", "ohne (sound -webm)");

        final String op;
        final String name;

        Operator(String op, String name) {
            this.op = op;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
