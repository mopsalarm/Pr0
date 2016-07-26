package com.pr0gramm.app.orm;

import android.annotation.TargetApi;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Ordering;
import com.pr0gramm.app.feed.Vote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.google.common.collect.Lists.transform;

/**
 */
public class CachedVote {
    private static final Logger logger = LoggerFactory.getLogger("CachedVote");

    public final long itemId;
    public final Type type;
    public final Vote vote;

    private CachedVote(long itemId, Type type, Vote vote) {
        this.itemId = itemId;
        this.type = type;
        this.vote = vote;
    }

    private static long voteId(Type type, long itemId) {
        return itemId * 10 + type.ordinal();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static Optional<CachedVote> find(SQLiteDatabase db, Type type, long itemId) {
        try (Cursor cursor = db.rawQuery("SELECT item_id, type, vote FROM cached_vote WHERE id=?",
                new String[]{String.valueOf(voteId(type, itemId))})) {

            if (!cursor.moveToNext())
                return Optional.absent();

            return Optional.of(ofCursor(cursor));
        }
    }

    private static CachedVote ofCursor(Cursor cursor) {
        return new CachedVote(
                cursor.getLong(0),
                Type.valueOf(cursor.getString(1)),
                Vote.valueOf(cursor.getString(2)));
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static List<CachedVote> find(SQLiteDatabase db, Type type, List<Long> ids) {
        if (ids.isEmpty())
            return Collections.emptyList();

        List<Long> sortedIds = Ordering.natural().immutableSortedCopy(ids);
        String encodedIds = Joiner.on(",").join(transform(sortedIds, id -> voteId(type, id)));
        try (Cursor cursor = db.rawQuery(
                "SELECT item_id, type, vote FROM cached_vote WHERE id IN (" + encodedIds + ")",
                new String[]{})) {

            List<CachedVote> result = new ArrayList<>();
            while (cursor.moveToNext()) {
                result.add(ofCursor(cursor));
            }

            return result;
        }
    }


    public static void quickSave(SQLiteDatabase database, Type type, long itemId, Vote vote) {
        String stmt = String.format(Locale.ROOT,
                "INSERT OR REPLACE INTO cached_vote (id, item_id, type, vote) VALUES (%d, %d, \"%s\", \"%s\")",
                voteId(type, itemId), itemId, type.name(), vote.name());

        database.execSQL(stmt);
    }

    public static void clear(SQLiteDatabase database) {
        database.execSQL("DELETE FROM cached_vote");
    }

    public static void prepareDatabase(SQLiteDatabase db) {
        logger.info("drop cached_vote__uid index, if it exists");
        db.execSQL("DROP INDEX IF EXISTS cached_vote__uid");

        logger.info("create cached_vote table if it does not exist.");
        db.execSQL("CREATE TABLE IF NOT EXISTS cached_vote (" +
                "id INTEGER PRIMARY KEY," +
                "type TEXT," +
                "vote TEXT," +
                "item_id INTEGER)");
    }

    public enum Type {
        ITEM, COMMENT, TAG
    }
}
