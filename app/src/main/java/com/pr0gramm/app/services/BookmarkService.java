package com.pr0gramm.app.services;

import android.app.Application;
import android.content.Context;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.orm.Bookmark;
import com.pr0gramm.app.ui.FeedFilterFormatter;

import java.util.List;

import rx.Observable;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;
import rx.util.async.Async;

import static com.pr0gramm.app.AndroidUtility.checkNotMainThread;

/**
 */
@Singleton
public class BookmarkService {
    private final BehaviorSubject<Void> onChange = BehaviorSubject.create((Void) null);
    private final Context context;

    @Inject
    public BookmarkService(Application context) {
        this.context = context;
    }

    public Observable<Bookmark> create(FeedFilter filter, String title) {
        return Async.start(() -> {
            // check if here is an existing item
            Optional<Bookmark> existing = Bookmark.byFilter(filter);
            if (existing.isPresent())
                return existing.get();

            // create new entry
            Bookmark entry = Bookmark.of(filter, title);
            entry.save();
            triggerChange();
            return entry;
        });
    }

    /**
     * Returns an observable producing "true", if the item is bookmarkable.
     * The observable produces "false" otherwise.
     *
     * @param filter The filter that the user wants to bookmark.
     */
    public Observable<Boolean> isBookmarkable(FeedFilter filter) {
        if (filter.isBasic())
            return Observable.just(false);

        if (filter.getLikes().isPresent())
            return Observable.just(false);

        // check if already in database
        return Async.start(() -> !Bookmark.byFilter(filter).isPresent());
    }

    private void triggerChange() {
        onChange.onNext(null);
    }

    public Observable<List<Bookmark>> get() {
        return onChange.subscribeOn(Schedulers.io()).map(ignored -> list());
    }

    /**
     * Blockingly list the bookmarks, ordered by title.
     *
     * @return The current bookmarks
     */
    private List<Bookmark> list() {
        checkNotMainThread();
        return Bookmark.find(Bookmark.class, null, null, null, "title ASC", null);
    }

    /**
     * Delete the bookmark. This method will not block.
     *
     * @param bookmark The bookmark that is to be deleted.
     */
    public Observable<Void> delete(Bookmark bookmark) {
        return Async.<Void>start(() -> {
            bookmark.delete();
            triggerChange();
            return null;
        }, Schedulers.io()).ignoreElements();
    }

    /**
     * Creates a bookmark for the filter. The title is auto generated.
     *
     * @param filter The filter to create a bookmark for.
     * @return The bookmark for that filter
     */
    public Observable<Bookmark> create(FeedFilter filter) {
        return create(filter, FeedFilterFormatter.format(context, filter));
    }
}
