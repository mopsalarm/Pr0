package com.pr0gramm.app.util;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.pr0gramm.app.orm.BenisRecord;
import com.pr0gramm.app.orm.Bookmark;
import com.pr0gramm.app.orm.CachedVote;
import com.pr0gramm.app.services.preloading.DatabasePreloadManager;
import com.squareup.sqlbrite.BriteDatabase;

/**
 */

public class Databases {
    private Databases() {
    }

    public static void withTransaction(SQLiteDatabase db, Runnable inTxRunnable) {
        db.beginTransaction();
        try {
            inTxRunnable.run();
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public static void withTransaction(BriteDatabase db, Runnable inTxRunnable) {
        withTransaction(db.getWritableDatabase(), inTxRunnable);
    }

    public static class PlainOpenHelper extends SQLiteOpenHelper {
        public PlainOpenHelper(Context context) {
            super(context, "pr0gramm.db", null, 9);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            CachedVote.Companion.prepareDatabase(db);
            BenisRecord.Companion.prepareDatabase(db);
            Bookmark.Companion.prepareDatabase(db);
            DatabasePreloadManager.Companion.onCreate(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onCreate(db);
        }
    }
}
