package com.pr0gramm.app.ui.intro.slides;

import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.services.BookmarkService;

import rx.Observable;

/**
 */
class BookmarkActionItem extends ActionItem {
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
        bookmarkService.create(filter, title);
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
