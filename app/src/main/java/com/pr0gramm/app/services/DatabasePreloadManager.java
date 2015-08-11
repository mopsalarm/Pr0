package com.pr0gramm.app.services;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.squareup.sqlbrite.BriteDatabase;
import com.squareup.sqlbrite.SqlBrite;

import org.joda.time.Instant;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import rx.Observable;

/**
 */
@Singleton
public class DatabasePreloadManager implements PreloadManager {
    private static final String TABLE_NAME = "preload";

    private static final String QUERY_ALL_ITEM_IDS = "SELECT * FROM " + TABLE_NAME;

    private final Observable<BriteDatabase> database;
    private final AtomicReference<ImmutableMap<Long, PreloadItem>> preloadCache =
            new AtomicReference<>(ImmutableMap.<Long, PreloadItem>of());

    @Inject
    public DatabasePreloadManager(Observable<BriteDatabase> database) {
        this.database = database;

        this.database
                .flatMap(db -> db.createQuery(TABLE_NAME, QUERY_ALL_ITEM_IDS))
                .map(SqlBrite.Query::run)
                .map(this::readPreloadEntriesFromCursor)
                .subscribe(preloadCache::set);
    }

    private ImmutableMap<Long, PreloadItem> readPreloadEntriesFromCursor(Cursor cursor) {
        ImmutableMap.Builder<Long, PreloadItem> result = new ImmutableMap.Builder<>();

        int cItemId = cursor.getColumnIndexOrThrow("itemId");
        int cCreation = cursor.getColumnIndexOrThrow("creation");
        int cMedia = cursor.getColumnIndexOrThrow("media");
        int cThumbnail = cursor.getColumnIndexOrThrow("thumbnail");
        while (cursor.moveToNext()) {
            result.put(cursor.getLong(cItemId), ImmutablePreloadItem.builder()
                    .itemId(cursor.getLong(cItemId))
                    .creation(new Instant(cursor.getLong(cCreation)))
                    .media(new File(cursor.getString(cMedia)))
                    .thumbnail(new File(cursor.getString(cThumbnail)))
                    .build());
        }

        return result.build();
    }

    /**
     * Inserts the given entry blockingly into the database.
     */
    @Override
    public void store(PreloadItem entry) {
        ContentValues values = new ContentValues();
        values.put("itemId", entry.itemId());
        values.put("creation", entry.creation().getMillis());
        values.put("media", entry.media().getPath());
        values.put("thumbnail", entry.thumbnail().getPath());
        db().insert(TABLE_NAME, values);
    }

    /**
     * Checks if an entry with the given itemId already exists in the database.
     */
    @Override
    public boolean exists(long itemId) {
        return preloadCache.get().containsKey(itemId);
    }

    /**
     * Returns the {@link PreloadItem} with a given id.
     */
    @Override
    public Optional<PreloadItem> get(long itemId) {
        return Optional.fromNullable(preloadCache.get().get(itemId));
    }

    /**
     * Returns a list of all preloaded items.
     */
    public ImmutableCollection<PreloadItem> all() {
        return preloadCache.get().values();
    }

    private BriteDatabase db() {
        return database.toBlocking().single();
    }

    public static void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "itemId INT NOT NULL," +
                "creation INT NOT NULL," +
                "media TEXT NOT NULL," +
                "thumbnail TEXT NOT NULL)");
    }
}
