package com.pr0gramm.app.orm;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import com.google.common.base.Optional;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.feed.FeedType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Objects.equal;
import static com.google.common.collect.Iterables.tryFind;

/**
 */
public final class Bookmark {
    private static final Logger logger = LoggerFactory.getLogger("Bookmark");

    private final String title;
    private final String filterTags;
    private final String filterUsername;
    private final String filterFeedType;

    private Bookmark(String title, String filterTags, String filterUsername, String filterFeedType) {
        this.title = title;
        this.filterTags = filterTags;
        this.filterUsername = filterUsername;
        this.filterFeedType = filterFeedType;
    }

    public String getTitle() {
        return title;
    }

    public FeedFilter asFeedFilter() {
        FeedFilter filter = new FeedFilter()
                .withFeedType(FeedType.valueOf(filterFeedType));

        if (filterTags != null)
            filter = filter.withTags(filterTags);

        if (filterUsername != null)
            filter = filter.withUser(filterUsername);

        return filter;
    }

    public static Bookmark of(FeedFilter filter, String title) {
        String filterTags = filter.getTags().orNull();
        String filterUsername = filter.getUsername().orNull();
        String filterFeedType = filter.getFeedType().toString();
        return new Bookmark(title, filterTags, filterUsername, filterFeedType);
    }

    public static Optional<Bookmark> byFilter(SQLiteDatabase database, FeedFilter filter) {
        return tryFind(Bookmark.all(database), bookmark -> equal(filter, bookmark.asFeedFilter()));
    }

    public static void save(SQLiteDatabase db, Bookmark bookmark) {
        ContentValues cv = new ContentValues();
        cv.put("title", bookmark.title);
        cv.put("filter_tags", bookmark.filterTags);
        cv.put("filter_username", bookmark.filterUsername);
        cv.put("filter_feed_type", bookmark.filterFeedType);

        db.insert("bookmark", null, cv);
    }

    public static void delete(SQLiteDatabase db, Bookmark bookmark) {
        db.delete("bookmark", "title=?", new String[]{bookmark.title});
    }

    public static void prepareDatabase(SQLiteDatabase db) {
        logger.info("create table bookmark if not exists");
        db.execSQL("CREATE TABLE IF NOT EXISTS bookmark (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "filter_feed_type TEXT," +
                "filter_tags TEXT," +
                "filter_username TEXT," +
                "title TEXT)");
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static List<Bookmark> all(SQLiteDatabase database) {
        ArrayList<Bookmark> bookmarks = new ArrayList<>();
        try (Cursor cursor = database.rawQuery("SELECT title, filter_tags, filter_username, filter_feed_type FROM bookmark ORDER BY title ASC", null)) {
            while (cursor.moveToNext()) {
                String title = cursor.getString(0);
                String filterTags = cursor.getString(1);
                String filterUsername = cursor.getString(2);
                String filterFeedType = cursor.getString(3);

                Bookmark bookmark = new Bookmark(title, filterTags, filterUsername, filterFeedType);
                bookmarks.add(bookmark);
            }
        }

        return bookmarks;
    }
}
