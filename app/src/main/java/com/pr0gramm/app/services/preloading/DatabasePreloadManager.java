package com.pr0gramm.app.services.preloading;

import android.annotation.SuppressLint;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import rx.Observable;

/**
 */
@Singleton
public class DatabasePreloadManager implements PreloadManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabasePreloadManager.class);

    private static final String TABLE_NAME = "preload_2";
    private static final String QUERY_ALL_ITEM_IDS = "SELECT * FROM " + TABLE_NAME;

    private final Observable<BriteDatabase> database;
    private final AtomicReference<ImmutableMap<Long, PreloadItem>> preloadCache =
            new AtomicReference<>(ImmutableMap.<Long, PreloadItem>of());

    @Inject
    public DatabasePreloadManager(Observable<BriteDatabase> database) {
        this.database = database;

        queryAllItems().subscribe(this::setPreloadCache);
    }

    private void setPreloadCache(ImmutableMap<Long, PreloadItem> items) {
        List<PreloadItem> missing = new ArrayList<>();
        for (PreloadItem item : items.values()) {
            if (!item.thumbnail().exists() || !item.media().exists()) {
                missing.add(item);
            }
        }

        if (!missing.isEmpty()) {
            BriteDatabase db = db();
            try (BriteDatabase.Transaction tx = db.newTransaction()) {
                deleteTx(db, missing);
                tx.markSuccessful();
            }
        } else {
            preloadCache.set(items);
        }
    }

    private Observable<ImmutableMap<Long, PreloadItem>> queryAllItems() {
        return this.database
                .flatMap(db -> db.createQuery(TABLE_NAME, QUERY_ALL_ITEM_IDS))
                .map(SqlBrite.Query::run)
                .map(this::readPreloadEntriesFromCursor);
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
        db().insert(TABLE_NAME, values, SQLiteDatabase.CONFLICT_REPLACE);
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

    @SuppressLint("NewApi")
    @Override
    public void deleteBefore(Instant threshold) {
        logger.info("Removing all files preloaded before {}", threshold);

        BriteDatabase db = db();

        try(BriteDatabase.Transaction tx = db.newTransaction()) {
            try (Cursor cursor = db.query("SELECT * FROM " + TABLE_NAME + " WHERE creation < ?",
                    String.valueOf(threshold.getMillis()))) {

                deleteTx(db, readPreloadEntriesFromCursor(cursor).values());
            }

            tx.markSuccessful();
        }
    }

    private void deleteTx(BriteDatabase db, Iterable<PreloadItem> items) {
        for (PreloadItem item : items) {
            logger.info("Removing files for itemId={}", item.itemId());

            if (!item.media().delete())
                logger.warn("Could not delete media file {}", item.media());

            if (!item.thumbnail().delete())
                logger.warn("Could not delete thumbnail file {}", item.thumbnail());

            // delete entry from database
            db.delete(TABLE_NAME, "itemId=?", String.valueOf(item.itemId()));
        }
    }

    /**
     * Returns a list of all preloaded items.
     */
    public Observable<ImmutableCollection<PreloadItem>> all() {
        return queryAllItems().map(ImmutableMap::values);
    }

    private BriteDatabase db() {
        return database.toBlocking().single();
    }

    public static void onCreate(SQLiteDatabase db) {
        logger.info("initializing sqlite database");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "itemId INT NOT NULL UNIQUE," +
                "creation INT NOT NULL," +
                "media TEXT NOT NULL," +
                "thumbnail TEXT NOT NULL)");
    }
}
