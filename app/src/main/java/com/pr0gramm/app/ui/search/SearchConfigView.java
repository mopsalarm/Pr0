package com.pr0gramm.app.ui.search;

import android.content.Context;
import android.graphics.Color;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.widget.PopupMenu;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.ui.CenteredTextDrawable;
import com.pr0gramm.app.util.AndroidUtility;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.Observable;
import rx.subjects.BehaviorSubject;

import static com.trello.rxlifecycle.android.RxLifecycleAndroid.bindView;

/**
 */
public class SearchConfigView extends FrameLayout implements SearchView {
    private final BehaviorSubject<SearchView> searchViewChanged = BehaviorSubject.create();

    private SearchType currentSearchType;
    private CenteredTextDrawable searchTypeIcon;

    @BindView(R.id.search_type)
    ImageButton searchTypeSelector;

    public SearchConfigView(Context context) {
        super(context);
        initializeView();
    }

    public SearchConfigView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializeView();
    }

    public SearchConfigView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initializeView();
    }

    private void initializeView() {
        // inflate and bind the sub views.
        View.inflate(getContext(), R.layout.search_config_view, this);
        ButterKnife.bind(this);

        setBackgroundColor(ColorUtils.setAlphaComponent(
                ContextCompat.getColor(getContext(), ThemeHelper.primaryColor()), 0x20));

        searchTypeIcon = new CenteredTextDrawable("?");
        searchTypeIcon.setTextColor(Color.BLACK);
        searchTypeIcon.setTextSize(AndroidUtility.dp(getContext(), 12));
        searchTypeSelector.setImageDrawable(searchTypeIcon);

        onSearchTypeSelected(SearchTypes.get(0));
    }

    @OnClick(R.id.search_type)
    public void onSearchTypeSelectorClicked() {
        PopupMenu menu = new PopupMenu(getContext(), searchTypeSelector);
        for (int idx = 0; idx < SearchTypes.size(); idx++) {
            SearchType type = SearchTypes.get(idx);
            menu.getMenu().add(Menu.NONE, idx, idx, type.name);
        }

        menu.setOnMenuItemClickListener(item -> {
            onSearchTypeSelected(SearchTypes.get(item.getItemId()));
            return true;
        });

        menu.show();
    }

    private void onSearchTypeSelected(SearchType type) {
        if (isOtherSearchType(type)) {
            if (getChildCount() > 1) {
                // remove first view
                removeViews(0, 1);
            }

            SearchView searchView = type.viewFactory.makeView(getContext());

            // build params to nicely position the view
            LayoutParams params = new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT);

            params.gravity = Gravity.CENTER_VERTICAL;

            View view = searchView.view();
            addView(view, 0, params);

            searchTypeIcon.setText(type.icon);
            currentSearchType = type;
            searchViewChanged.onNext(searchView);
        }
    }

    public void switchToOperatorSearchType() {
        onSearchTypeSelected(SearchTypes.get(1));
    }

    private boolean isOtherSearchType(SearchType type) {
        return this.currentSearchType != type;
    }

    @Override
    public View view() {
        return this;
    }

    @Override
    public void focus() {
        SearchView view = searchViewChanged.getValue();
        if (view != null)
            view.focus();
    }

    @Override
    public Observable<Boolean> valid() {
        return searchViewChanged
                .switchMap(view -> view.valid().compose(bindView(view.view())));
    }

    @Override
    public Observable<String> queryString() {
        return searchViewChanged
                .switchMap(view -> view.queryString().compose(bindView(view.view())));
    }

    private static class SearchType {
        final String icon;
        final String name;
        final ViewFactory viewFactory;

        SearchType(String icon, String name, ViewFactory viewFactory) {
            this.icon = icon;
            this.name = name;
            this.viewFactory = viewFactory;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private interface ViewFactory {
        SearchView makeView(Context context);
    }

    private static ViewFactory newFixViewFactory(String title, String term) {
        return context -> SearchViews.staticSearchView(context, title, term);
    }

    private static List<SearchType> SearchTypes = ImmutableList.of(
            new SearchType("tag", "Tag", SearchViews::editTextSearchView),
            new SearchType("op", "und/oder/minus", SearchViewOperator::new),
            new SearchType("user", "User", SearchViews::userSearchView),
            new SearchType("text", "Enhält Text", newFixViewFactory("Enthält Text", "f:text")),
            new SearchType("top?", "Ist in top", newFixViewFactory("Ist in top", "f:top")),
            new SearchType("ton", "Hat Ton", newFixViewFactory("Hat ton", "f:sound")),
            new SearchType("sfw?", "Filter", SearchViewContentType::new),
            new SearchType("ben\nis", "Bewertung", SearchViewScore::new)
    );
}
