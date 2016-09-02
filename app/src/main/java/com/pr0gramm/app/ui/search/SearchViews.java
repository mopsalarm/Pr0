package com.pr0gramm.app.ui.search;

import android.content.Context;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import com.google.common.collect.ImmutableList;
import com.jakewharton.rxbinding.widget.RxTextView;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.R;
import com.pr0gramm.app.ui.UsernameAutoCompleteAdapter;

import java.util.List;

import butterknife.ButterKnife;
import rx.Observable;

import static rx.Observable.just;

/**
 */
class SearchViews {
    private SearchViews() {
    }

    public static SearchView editTextSearchView(Context context) {
        View view = View.inflate(context, R.layout.search_view_edit_text, null);

        TextView textView = ButterKnife.findById(view, R.id.search_text);
        textView.setHint(randomTagSuggestion());

        return new SearchView() {
            @Override
            public View view() {
                return view;
            }

            @Override
            public Observable<Boolean> valid() {
                return queryString().map(text -> text.length() > 0);
            }

            @Override
            public Observable<String> queryString() {
                return RxTextView
                        .textChanges(textView)
                        .map(text -> text.toString().trim().toLowerCase().replaceAll("[^0-9a-zäöüß: ]", ""));
            }

            @Override
            public void focus() {
                textView.requestFocus();
            }
        };
    }

    public static SearchView userSearchView(Context context) {
        View view = View.inflate(context, R.layout.search_view_user, null);

        AutoCompleteTextView textView = ButterKnife.findById(view, R.id.search_text);

        textView.setAdapter(new UsernameAutoCompleteAdapter(
                Dagger.appComponent(context).suggestionService(), context,
                "", android.R.layout.simple_dropdown_item_1line));

        return new SearchView() {
            @Override
            public View view() {
                return view;
            }

            @Override
            public Observable<Boolean> valid() {
                return queryString().map(text -> text.length() > 2);
            }

            @Override
            public Observable<String> queryString() {
                return RxTextView
                        .textChanges(textView)
                        .map(text -> text.toString().trim().toLowerCase().replaceAll("[^0-9a-z: ]", ""))
                        .map(text -> "u:" + text);
            }

            @Override
            public void focus() {
                textView.requestFocus();
            }
        };
    }

    public static SearchView staticSearchView(Context context, String title, String searchText) {
        View view = View.inflate(context, R.layout.search_view_text, null);

        TextView textView = ButterKnife.findById(view, R.id.search_text);
        textView.setText(title);

        return new SearchView() {
            @Override
            public View view() {
                return view;
            }

            @Override
            public Observable<Boolean> valid() {
                return just(true);
            }

            @Override
            public Observable<String> queryString() {
                return just(searchText);
            }

            @Override
            public void focus() {
            }
        };
    }

    private static String randomTagSuggestion() {
        return SUGGESTIONS.get((int) (Math.random() * SUGGESTIONS.size()));
    }

    private static final List<String> SUGGESTIONS = ImmutableList.of(
            "kadse", "kefer", "porn", "0815", "nixname", "sound", "webm", "gif",
            "süßvieh", "pr0gramm", "cha0s", "gamb", "titten", "stefipant");
}
