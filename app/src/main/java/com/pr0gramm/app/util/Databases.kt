package com.pr0gramm.app.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

import com.pr0gramm.app.orm.BenisRecord
import com.pr0gramm.app.orm.Bookmark
import com.pr0gramm.app.orm.CachedVote
import com.pr0gramm.app.services.preloading.DatabasePreloadManager
import com.squareup.sqlbrite.BriteDatabase

/**
 */

object Databases {
    const val DATABASE_VERSION: Int = 9

    inline fun withTransaction(db: SQLiteDatabase, inTxRunnable: () -> Unit) {
        db.beginTransaction()
        try {
            inTxRunnable()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    inline fun withTransaction(db: BriteDatabase, inTxRunnable: () -> Unit) {
        withTransaction(db.writableDatabase, inTxRunnable)
    }

    class PlainOpenHelper(context: Context) : SQLiteOpenHelper(context, "pr0gramm.db", null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            CachedVote.prepareDatabase(db)
            BenisRecord.prepareDatabase(db)
            Bookmark.prepareDatabase(db)
            DatabasePreloadManager.onCreate(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            onCreate(db)
        }
    }
}
