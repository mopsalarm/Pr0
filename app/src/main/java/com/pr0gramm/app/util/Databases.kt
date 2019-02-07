package com.pr0gramm.app.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.squareup.sqlbrite.BriteDatabase

/**
 */

object Databases {
    const val DATABASE_VERSION: Int = 11

    inline fun withTransaction(db: SQLiteDatabase, inTxRunnable: () -> Unit) {
        db.beginTransaction()
        try {
            inTxRunnable()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    inline fun withTransaction(db: BriteDatabase, crossinline inTxRunnable: () -> Unit) {
        withTransaction(db.writableDatabase, inTxRunnable)
    }

    class PlainOpenHelper(context: Context) : SQLiteOpenHelper(context, "pr0gramm.db", null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            onUpgrade(db, 0, DATABASE_VERSION)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("""CREATE TABLE cached_vote (id INTEGER PRIMARY KEY, type TEXT, vote TEXT, item_id INTEGER)""")

            db.execSQL("""CREATE TABLE benis_record (id INTEGER PRIMARY KEY AUTOINCREMENT, benis INTEGER, time INTEGER, owner_id INTEGER)""")
            db.execSQL("""CREATE INDEX benis_record__time ON benis_record(time)""")

            db.execSQL("""CREATE TABLE preload_2 (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    itemId INT NOT NULL UNIQUE,
                    creation INT NOT NULL,
                    media TEXT NOT NULL,
                    thumbnail TEXT NOT NULL)""")


            if (oldVersion < 11) {
                db.execSQL("ALTER TABLE preload_2 RENAME TO preload_items")
            }
        }
    }
}
