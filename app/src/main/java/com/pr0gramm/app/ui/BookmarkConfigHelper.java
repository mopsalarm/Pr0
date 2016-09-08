package com.pr0gramm.app.ui;

import android.content.Context;
import android.support.v7.app.AlertDialog;

import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.services.BookmarkService;

import java.util.BitSet;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.functions.Action1;

/**
 */
@Singleton
public class BookmarkConfigHelper {
    private final String[] titles = {
            "Kein Ton",
            "Keine Videos",
            "Original Content",
            "0815 & Süßvieh",
            "Nur Bilder",
            "Reposts in Top",
            "Ton nur mit Untertitel",
            "Zufall",
            "Kontrovers",
            "Text"
    };

    private final ImmutableList<Action1<Boolean>> actions = ImmutableList.of(
            makeBookmark(titles[0], new FeedFilter().withTags("? -sound")),
            makeBookmark(titles[1], new FeedFilter().withTags("? -webm")),
            makeBookmark(titles[2], new FeedFilter().withTags("original content")),
            makeBookmark(titles[3], new FeedFilter().withTags("? 0815|süßvieh|(ficken halt)|(aber schicks keinem)")),
            makeBookmark(titles[4], new FeedFilter().withTags("? -webm -gif")),
            makeBookmark(titles[5], new FeedFilter().withTags("? repost & f:top")),
            makeBookmark(titles[6], new FeedFilter().withTags("? (-sound | (untertitel & -404))")),
            setPreferenceFlag("pref_show_category_random"),
            setPreferenceFlag("pref_show_category_controversial"),
            setPreferenceFlag("pref_show_category_text"));

    private final boolean[] preSelected = {false, false, true, false, false, false, false, true, true, false};

    private final BookmarkService bookmarkService;
    private final Settings settings;

    @Inject
    public BookmarkConfigHelper(BookmarkService bookmarkService, Settings settings) {
        this.bookmarkService = bookmarkService;
        this.settings = settings;
    }

    private Action1<Boolean> makeBookmark(String title, FeedFilter filter) {
        return (on) -> {
            if (on) {
                bookmarkService.create(filter, title)
                        .onErrorResumeNext(Observable.empty())
                        .subscribe();
            } else {
                bookmarkService.get()
                        .onErrorResumeNext(Observable.empty())
                        .take(1)
                        .flatMapIterable(items -> items)
                        .filter(bookmark -> bookmark.asFeedFilter().equals(filter))
                        .flatMap(bookmark -> bookmarkService.delete(bookmark).toObservable().onErrorResumeNext(Observable.empty()))
                        .subscribe();
            }
        };
    }

    private Action1<Boolean> setPreferenceFlag(String pref) {
        return on -> settings.edit().putBoolean(pref, on).apply();
    }

    public void show(Context context) {
        BitSet selection = new BitSet(titles.length);
        for (int idx = 0; idx < preSelected.length; idx++)
            selection.set(idx, preSelected[idx]);

        new AlertDialog.Builder(context)
                .setTitle(R.string.bookmark_config_title)
                .setMultiChoiceItems(titles, preSelected, (dialog, which, isChecked) -> selection.set(which, isChecked))
                .setPositiveButton(R.string.okay, (dialog, w) -> {
                    for (int idx = 0; idx < titles.length; idx++) {
                        actions.get(idx).call(selection.get(idx));
                    }
                })
                .show();
    }
}
