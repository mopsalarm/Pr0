package com.pr0gramm.app.ui.search;

import android.view.View;

import rx.Observable;

/**
 */
public interface SearchView {
    Observable<Boolean> valid();

    View view();

    void focus();

    Observable<String> queryString();
}
