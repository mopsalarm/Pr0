package com.pr0gramm.app.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import com.squareup.sqlbrite.BriteDatabase

/**
 */

object Databases {
    private val logger = Logger("Database")


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

    // remember to update the database version if you add new migrations.
    private const val DATABASE_VERSION: Int = 11

    class PlainOpenHelper(context: Context) : SQLiteOpenHelper(context, "pr0gramm.db", null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            migrate(db, 0, recreateOnError = true)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            migrate(db, oldVersion, recreateOnError = true)
        }
    }

    private fun migrate(db: SQLiteDatabase, oldVersion: Int, recreateOnError: Boolean) {
        fun upgradeTo(version: Int, block: () -> Unit) {
            if (oldVersion < version) {
                logger.info { "Upgrade to version $version" }
                block()
            }
        }

        try {
            upgradeTo(version = 10) {
                db.execSQL("""CREATE TABLE cached_vote (id INTEGER PRIMARY KEY, type TEXT, vote TEXT, item_id INTEGER)""")

                db.execSQL("""CREATE TABLE benis_record (id INTEGER PRIMARY KEY AUTOINCREMENT, benis INTEGER, time INTEGER, owner_id INTEGER)""")
                db.execSQL("""CREATE INDEX benis_record__time ON benis_record(time)""")

                db.execSQL("""CREATE TABLE preload_2 (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    itemId INT NOT NULL UNIQUE,
                    creation INT NOT NULL,
                    media TEXT NOT NULL,
                    thumbnail TEXT NOT NULL)""")
            }

            upgradeTo(version = 11) {
                db.execSQL("ALTER TABLE preload_2 RENAME TO preload_items")
            }
        } catch (err: SQLiteException) {
            AndroidUtility.logToCrashlytics(err)

            if (recreateOnError) {
                recreate(db)
                return
            }

            // migration failed, re-throw exception
            throw err
        }
    }

    private fun recreate(db: SQLiteDatabase) {
        logger.warn { "Create database" }

        db.execSQL("DROP TABLE IF EXISTS preload_2")
        db.execSQL("DROP TABLE IF EXISTS preload_items")
        db.execSQL("DROP TABLE IF EXISTS benis_record")
        db.execSQL("DROP TABLE IF EXISTS cached_vote")

        migrate(db, 0, recreateOnError = false)
    }
}
