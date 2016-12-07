package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.support.v7.widget.AppCompatCheckBox;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.jakewharton.rxbinding.widget.RxSeekBar;
import com.jakewharton.rxbinding.widget.RxTextView;
import com.pr0gramm.app.R;
import com.pr0gramm.app.util.AndroidUtility;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Observable;
import rx.subjects.PublishSubject;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.google.common.base.CharMatcher.javaLetterOrDigit;
import static com.google.common.base.CharMatcher.whitespace;

/**
 * View for more search options.
 */

public class SearchOptionsView extends LinearLayout {
    private final PublishSubject<String> searchQuery = PublishSubject.create();

    final Set<String> withoutTags = new HashSet<>();

    @BindView(R.id.search_term_container)
    View searchTermContainer;

    @BindView(R.id.search_term)
    EditText searchTermView;

    @BindView(R.id.search_button)
    ImageButton searchButton;

    @BindView(R.id.minimum_benis_slider)
    SeekBar minimumScoreSlider;

    @BindView(R.id.minimum_benis_value)
    TextView minimumBenisValue;

    @BindView(R.id.without_tags_text)
    EditText customWithoutTagsView;

    public SearchOptionsView(Context context) {
        super(context);
        initView();
    }

    public SearchOptionsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public SearchOptionsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    private void initView() {
        setOrientation(VERTICAL);

        inflate(getContext(), R.layout.view_search, this);
        ButterKnife.bind(this);

        addTextCheckboxes();

        searchButton.setOnClickListener(view -> handleSearchButtonClicked());

        minimumScoreSlider.setMax(1000);
        minimumScoreSlider.setKeyProgressIncrement(5);

        // update the value field with the slider
        RxSeekBar.changes(minimumScoreSlider)
                .map(value -> String.valueOf(roundScoreValue(value)))
                .subscribe(minimumBenisValue::setText);

        // enter on search field should start the search
        RxTextView
                .editorActions(searchTermView, action -> action == EditorInfo.IME_ACTION_SEARCH)
                .subscribe(e -> handleSearchButtonClicked());
    }

    private int roundScoreValue(int value) {
        value = (int) (Math.pow(value / 100.0, 2.0) * 90);
        return (int) (0.5 + value / 100.0) * 100;
    }

    private void handleSearchButtonClicked() {
        boolean extendedSearch = false;

        List<String> terms = new ArrayList<>();

        // get the base search-term
        String baseTerm = String.valueOf(searchTermView.getText()).trim();
        if (baseTerm.startsWith("?")) {
            extendedSearch = true;
            baseTerm = baseTerm.substring(1).trim();
        }

        if (!baseTerm.isEmpty()) {
            terms.add(baseTerm);
        }

        // add minimum benis score selector
        int score = roundScoreValue(minimumScoreSlider.getProgress());
        if (score > 0) {
            extendedSearch = true;
            terms.add(String.format("s:%d", score));
        }

        // add tags to ignore
        HashSet<String> withoutTags = buildCurrentWithoutTags();
        if (withoutTags.size() > 0) {
            extendedSearch = true;
            String tags = Joiner.on("|").join(withoutTags);
            terms.add(String.format("-(%s)", tags));
        }

        // empty or actually simple search?
        if (Iterables.all(terms, javaLetterOrDigit().or(whitespace())::matchesAllOf)) {
            extendedSearch = false;
        }

        // combine everything together
        String searchTerm = Joiner.on(" & ").join(terms);
        if (extendedSearch || terms.size() > 1) {
            searchTerm = "? " + searchTerm;
        }

        searchQuery.onNext(searchTerm);
    }

    private HashSet<String> buildCurrentWithoutTags() {
        // use tags from check-boxes
        HashSet<String> withoutTags = new HashSet<>(this.withoutTags);

        // add custom tags
        withoutTags.addAll(Splitter
                .on(whitespace())
                .trimResults()
                .omitEmptyStrings()
                .splitToList(String.valueOf(customWithoutTagsView.getText()).toLowerCase()));

        return withoutTags;
    }

    public Observable<String> searchQuery() {
        return searchQuery;
    }

    private void addTextCheckboxes() {
        ViewGroup container = ButterKnife.findById(this, R.id.without_checks);

        LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.leftMargin = AndroidUtility.dp(getContext(), 8);

        List<String> tags = ImmutableList.of("f:sound", "webm", "f:repost", "m:ftb");
        List<String> names = ImmutableList.of("sound", "webm", "repost", "ftb");

        for (int idx = 0; idx < tags.size(); idx++) {
            String tagValue = tags.get(idx);

            CheckBox checkbox = new AppCompatCheckBox(getContext());
            checkbox.setLayoutParams(params);
            checkbox.setText(names.get(idx));
            checkbox.setOnCheckedChangeListener((view, isChecked) -> {
                if (isChecked) {
                    withoutTags.add(tagValue);
                } else {
                    withoutTags.remove(tagValue);
                }
            });

            container.addView(checkbox);
        }
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, 0, right, bottom);

        // move top padding to search view (container)
        searchTermContainer.setPadding(0, top, 0, 0);
    }

    public void requestSearchFocus() {
        // focus view and open keyboard
        post(() -> AndroidUtility.showSoftKeyboard(searchTermView));
    }

    public void setQueryHint(String queryHint) {
        searchTermView.setHint(queryHint);
    }
}
