package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.widget.AppCompatCheckBox;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
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
import com.pr0gramm.app.util.CustomTabsHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.Observable;
import rx.subjects.PublishSubject;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.google.common.base.CharMatcher.javaLetterOrDigit;
import static com.google.common.base.CharMatcher.whitespace;
import static java.util.Arrays.asList;

/**
 * View for more search options.
 */

public class SearchOptionsView extends LinearLayout {
    private final PublishSubject<String> searchQuery = PublishSubject.create();
    private final PublishSubject<Boolean> searchCanceled = PublishSubject.create();

    final Set<String> excludedTags = new HashSet<>();

    @BindView(R.id.search_term_container)
    View searchTermContainer;

    @BindView(R.id.search_term)
    EditText searchTermView;

    @BindView(R.id.minimum_benis_slider)
    SeekBar minimumScoreSlider;

    @BindView(R.id.minimum_benis_label)
    TextView minimumScoreLabel;

    @BindView(R.id.without_tags_text)
    EditText customExcludesView;

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

        updateTagsCheckboxes();

        minimumScoreSlider.setMax(1000);
        minimumScoreSlider.setKeyProgressIncrement(5);

        if (!isInEditMode()) {
            // update the value field with the slider
            RxSeekBar.changes(minimumScoreSlider)
                    .map(value -> formatMinimumScoreValue(roundScoreValue(value)))
                    .subscribe(minimumScoreLabel::setText);

            // enter on search field should start the search
            RxTextView
                    .editorActions(searchTermView, action -> action == EditorInfo.IME_ACTION_SEARCH)
                    .subscribe(e -> handleSearchButtonClicked());

            // and start search on custom tags view too.
            RxTextView
                    .editorActions(customExcludesView, action -> action == EditorInfo.IME_ACTION_SEARCH)
                    .subscribe(e -> handleSearchButtonClicked());
        }
    }

    /**
     * Resets the view back to its "empty" state.
     */
    @OnClick(R.id.reset_button)
    public void reset() {
        searchTermView.setText("");
        customExcludesView.setText("");
        minimumScoreSlider.setProgress(0);

        excludedTags.clear();
        updateTagsCheckboxes();
    }

    @OnClick(R.id.search_cancel)
    void cancel() {
        searchCanceled.onNext(true);
    }

    @OnClick(R.id.search_advanced)
    void showAdvancedHelpPage() {
        Uri uri = Uri.parse("https://github.com/mopsalarm/pr0gramm-tags/blob/master/README.md#tag-suche-für-pr0gramm");
        new CustomTabsHelper(getContext()).openCustomTab(uri);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        super.onSaveInstanceState();
        return currentState();
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(null);

        if (state instanceof Bundle) {
            applyState((Bundle) state);
        }
    }

    public Bundle currentState() {
        Bundle state = new Bundle();
        state.putInt("minScore", minimumScoreSlider.getProgress());
        state.putCharSequence("queryTerm", searchTermView.getText());
        state.putCharSequence("customWithoutTerm", customExcludesView.getText());
        state.putStringArray("selectedWithoutTags", excludedTags.toArray(new String[0]));
        return state;
    }

    public void applyState(Bundle state) {
        if (state == null) {
            return;
        }

        minimumScoreSlider.setProgress(state.getInt("minScore", 0));
        searchTermView.setText(state.getCharSequence("queryTerm", ""));
        customExcludesView.setText(state.getCharSequence("customWithoutTerm", ""));

        // clear original tags
        excludedTags.clear();

        // set new tags
        String[] tags = state.getStringArray("selectedWithoutTags");
        if (tags != null)
            excludedTags.addAll(asList(tags));

        // rebuild the checkboxes
        updateTagsCheckboxes();
    }

    @OnClick(R.id.search_button)
    void handleSearchButtonClicked() {
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
        HashSet<String> withoutTags = buildCurrentExcludedTags();
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

        // replace all new line characters (why would you add a new line?)
        searchTerm = searchTerm.replace('\n', ' ');

        searchQuery.onNext(searchTerm);
    }

    private int roundScoreValue(int value) {
        value = (int) (Math.pow(value / 100.0, 2.0) * 90);
        return (int) (0.5 + value / 100.0) * 100;
    }

    private String formatMinimumScoreValue(int score) {
        String formatted = String.valueOf(score);
        if (score == 0) {
            formatted = getContext().getString(R.string.search_score_ignored);
        }

        return getContext().getString(R.string.search_score, formatted);
    }

    private HashSet<String> buildCurrentExcludedTags() {
        // use tags from check-boxes
        HashSet<String> withoutTags = new HashSet<>(this.excludedTags);

        // add custom tags
        withoutTags.addAll(Splitter
                .on(whitespace())
                .trimResults()
                .omitEmptyStrings()
                .splitToList(String.valueOf(customExcludesView.getText()).toLowerCase()));

        return withoutTags;
    }

    public Observable<String> searchQuery() {
        return searchQuery;
    }

    public Observable<Boolean> searchCanceled() {
        return searchCanceled;
    }

    private void updateTagsCheckboxes() {
        ViewGroup container = ButterKnife.findById(this, R.id.without_checks);

        container.removeAllViews();

        LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.leftMargin = AndroidUtility.dp(getContext(), 8);

        List<String> tags = ImmutableList.of("f:sound", "webm", "f:repost", "m:ftb");
        List<String> names = ImmutableList.of("sound", "webm", "repost", "ftb");

        for (int idx = 0; idx < tags.size(); idx++) {
            String tagValue = tags.get(idx);

            CheckBox checkbox = new AppCompatCheckBox(getContext());
            checkbox.setText(names.get(idx));
            checkbox.setChecked(excludedTags.contains(tagValue));

            if (idx > 0) {
                checkbox.setLayoutParams(params);
            }

            checkbox.setOnCheckedChangeListener((view, isChecked) -> {
                if (isChecked) {
                    excludedTags.add(tagValue);
                } else {
                    excludedTags.remove(tagValue);
                }
            });

            container.addView(checkbox);
        }
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, 0, right, bottom);

        if (searchTermContainer != null) {
            // move top padding to search view (container)
            searchTermContainer.setPadding(0, top, 0, 0);
        }
    }

    public void requestSearchFocus() {
        // focus view and open keyboard
        // post(() -> AndroidUtility.showSoftKeyboard(searchTermView));

        post(() -> searchTermView.requestFocus());
    }

    public void setQueryHint(String queryHint) {
        searchTermView.setHint(queryHint);
    }
}
