package com.pr0gramm.app.services;

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.google.common.base.Optional;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.orm.Bookmark;
import com.pr0gramm.app.ui.FeedFilterFormatter;
import com.pr0gramm.app.util.BackgroundScheduler;
import com.pr0gramm.app.util.Holder;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Completable;
import rx.Observable;
import rx.Single;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;
import rx.util.async.Async;

import static com.pr0gramm.app.orm.Bookmark.byFilter;
import static com.pr0gramm.app.orm.Bookmark.of;
import static com.pr0gramm.app.util.AndroidUtility.checkNotMainThread;

/**
 */
@Singleton
public class BookmarkService {
    private final Subject<Void, Void> onChange = BehaviorSubject.create((Void) null).toSerialized();
    private final Context context;
    private final Holder<SQLiteDatabase> database;

    @Inject
    public BookmarkService(Application context, Holder<SQLiteDatabase> database) {
        this.context = context;
        this.database = database;
    }

    public Observable<Bookmark> create(FeedFilter filter, String title) {
        return Async.start(() -> {
            // check if here is an existing item
            Optional<Bookmark> existing = byFilter(database.value(), filter);
            if (existing.isPresent())
                return existing.get();

            // create new entry
            Bookmark entry = of(filter, title);
            Bookmark.save(database.value(), entry);

            triggerChange();

            return entry;
        }, BackgroundScheduler.instance());
    }

    /**
     * Returns an observable producing "true", if the item is bookmarkable.
     * The observable produces "false" otherwise.
     *
     * @param filter The filter that the user wants to bookmark.
     */
    public Single<Boolean> isBookmarkable(FeedFilter filter) {
        if (filter.isBasic())
            return Single.just(false);

        if (filter.getLikes().isPresent())
            return Single.just(false);

        // check if already in database
        return database.asSingle().map(db -> !Bookmark.byFilter(db, filter).isPresent());
    }

    private void triggerChange() {
        onChange.onNext(null);
    }

    public Observable<List<Bookmark>> get() {
        return onChange.subscribeOn(BackgroundScheduler.instance()).map(ignored -> list());
    }

    /**
     * Blockingly list the bookmarks, ordered by title.
     *
     * @return The current bookmarks
     */
    private List<Bookmark> list() {
        checkNotMainThread();
        return Bookmark.all(database.value());
    }

    /**
     * Delete the bookmark. This method will not block.
     *
     * @param bookmark The bookmark that is to be deleted.
     */
    public Completable delete(Bookmark bookmark) {
        return Async.<Void>start(() -> {
            Bookmark.delete(database.value(), bookmark);
            triggerChange();
            return null;
        }, BackgroundScheduler.instance()).toCompletable();
    }

    /**
     * Creates a bookmark for the filter. The title is auto generated.
     *
     * @param filter The filter to create a bookmark for.
     * @return The bookmark for that filter
     */
    public Observable<Bookmark> create(FeedFilter filter) {
        return create(filter, FeedFilterFormatter.format(context, filter).title);
    }
}
