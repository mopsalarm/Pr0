package com.pr0gramm.app.orm;

import android.annotation.TargetApi;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import org.joda.time.ReadableInstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 */
public class BenisRecord {
    private static final Logger logger = LoggerFactory.getLogger("BenisRecord");

    public final long time;
    public final int benis;

    private BenisRecord(long time, int benis) {
        this.time = time;
        this.benis = benis;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static List<BenisRecord> findValuesLaterThan(SQLiteDatabase db, int ownerId, ReadableInstant time) {
        try (Cursor cursor = db.query("benis_record",
                new String[]{"time", "benis"},
                "time >= ? and (owner_id=? or owner_id=0)",
                new String[]{String.valueOf(time.getMillis()), String.valueOf(ownerId)},
                null, null, "time ASC")) {

            List<BenisRecord> result = new ArrayList<>();
            while (cursor.moveToNext()) {
                result.add(new BenisRecord(
                        cursor.getLong(0),
                        cursor.getInt(1)));
            }

            return result;
        }
    }

    public static void storeValue(SQLiteDatabase db, int ownerId, int benis) {
        db.execSQL("INSERT INTO benis_record (owner_id, time, benis) VALUES (?, ?, ?)",
                new String[]{String.valueOf(ownerId),
                        String.valueOf(System.currentTimeMillis()),
                        String.valueOf(benis)});
    }

    public static void prepareDatabase(SQLiteDatabase db) {
        logger.info("setting up benis_record table");
        db.execSQL("CREATE TABLE IF NOT EXISTS benis_record (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "benis INTEGER," +
                "time INTEGER," +
                "owner_id INTEGER)");

        logger.info("create index on benis_record(time) if it does not exist");
        db.execSQL("CREATE INDEX IF NOT EXISTS benis_record__time ON benis_record(time)");
    }
}
