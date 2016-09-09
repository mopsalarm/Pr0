package com.pr0gramm.app.ui;

import android.content.Context;
import android.support.v7.app.AlertDialog;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.services.BookmarkService;

import java.util.BitSet;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;

/**
 */
@Singleton
public class BookmarkConfigHelper {
    private final String[] titles;
    private final ImmutableList<ActionItem> actions;

    @Inject
    public BookmarkConfigHelper(BookmarkService bookmarkService, Settings settings) {
        FeedFilter f = new FeedFilter();

        actions = ImmutableList.of(
                new BookmarkActionItem(bookmarkService, "Kein Ton", f.withTags("? -sound")),
                new BookmarkActionItem(bookmarkService, "Keine Videos", f.withTags("? -webm")),
                new BookmarkActionItem(bookmarkService, "Original Content", f.withTags("original content")),
                new BookmarkActionItem(bookmarkService, "0815 & Süßvieh", f.withTags("? 0815|süßvieh|(ficken halt)|(aber schicks keinem)")),
                new BookmarkActionItem(bookmarkService, "Nur Bilder", f.withTags("? -webm -gif")),
                new BookmarkActionItem(bookmarkService, "Reposts in Top", f.withTags("? repost & f:top")),
                new BookmarkActionItem(bookmarkService, "Ton nur mit Untertitel", f.withTags("? (-sound | (untertitel & -404))")),
                new SettingActionItem(settings, "Zufall", "pref_show_category_random"),
                new SettingActionItem(settings, "Kontrovers", "pref_show_category_controversial"),
                new SettingActionItem(settings, "Text", "pref_show_category_text"));

        titles = FluentIterable.from(actions).transform(item -> item.title).toArray(String.class);
    }

    public void show(Context context) {
        boolean[] preSelected = new boolean[actions.size()];

        BitSet selection = new BitSet(actions.size());
        for (int idx = 0; idx < actions.size(); idx++)
            selection.set(idx, preSelected[idx] = actions.get(idx).enabled());

        new AlertDialog.Builder(context)
                .setTitle(R.string.bookmark_config_title)
                .setMultiChoiceItems(titles, preSelected, (dialog, which, isChecked) -> selection.set(which, isChecked))
                .setPositiveButton(R.string.okay, (dialog, w) -> {
                    for (int idx = 0; idx < titles.length; idx++) {
                        if (selection.get(idx)) {
                            actions.get(idx).activate();
                        } else {
                            actions.get(idx).deactivate();
                        }
                    }
                })
                .show();
    }

    private static abstract class ActionItem {
        final String title;

        ActionItem(String title) {
            this.title = title;
        }

        public abstract boolean enabled();

        public abstract void activate();

        public abstract void deactivate();
    }

    private static class BookmarkActionItem extends ActionItem {
        private final BookmarkService bookmarkService;
        private final FeedFilter filter;

        BookmarkActionItem(BookmarkService bookmarkService, String title, FeedFilter filter) {
            super(title);
            this.bookmarkService = bookmarkService;
            this.filter = filter;
        }

        @Override
        public boolean enabled() {
            return bookmarkService.get()
                    .take(1)
                    .flatMapIterable(bookmarks -> bookmarks)
                    .exists(bookmark -> bookmark.asFeedFilter().equals(filter))
                    .onErrorResumeNext(Observable.empty())
                    .toBlocking()
                    .first();
        }

        @Override
        public void activate() {
            bookmarkService.create(filter, title)
                    .onErrorResumeNext(Observable.empty())
                    .subscribe();
        }

        @Override
        public void deactivate() {
            bookmarkService.get()
                    .take(1)
                    .onErrorResumeNext(Observable.empty())
                    .flatMapIterable(items -> items)
                    .filter(bookmark -> bookmark.asFeedFilter().equals(filter))
                    .flatMap(bookmark -> bookmarkService.delete(bookmark).toObservable().onErrorResumeNext(Observable.empty()))
                    .subscribe();
        }
    }

    private static class SettingActionItem extends ActionItem {
        private final Settings settings;
        private final String preference;

        SettingActionItem(Settings settings, String title, String preference) {
            super(title);
            this.settings = settings;
            this.preference = preference;
        }

        @Override
        public boolean enabled() {
            return settings.raw().getBoolean(preference, false);
        }

        @Override
        public void activate() {
            settings.edit().putBoolean(preference, true);
        }

        @Override
        public void deactivate() {
            settings.edit().putBoolean(preference, false);
        }
    }
}
