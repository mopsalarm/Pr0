package com.pr0gramm.app.services

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.pr0gramm.app.ApplicationClass
import com.pr0gramm.app.Instant
import com.pr0gramm.app.Logger
import com.pr0gramm.app.db.ScoreRecordQueries
import com.pr0gramm.app.orm.BenisRecord
import com.pr0gramm.app.time
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.ui.base.launchIgnoreErrors
import com.pr0gramm.app.util.di.injector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runInterruptible

class BenisRecordService(private val db: ScoreRecordQueries) {
    private val logger = Logger("BenisRecordService")

    suspend fun findValuesLaterThan(ownerId: Int, minTime: Instant): List<BenisRecord> {
        return db.list(minTime.millis, ownerId) { time, score -> BenisRecord(time, score) }
            .asFlow()
            .mapToList(Dispatchers.IO)
                .first()
    }

    suspend fun storeValue(ownerId: Int, benis: Int) {
        runInterruptible(Dispatchers.IO) {
            db.save(System.currentTimeMillis(), benis, ownerId)
        }
    }

    init {
        AsyncScope.launchIgnoreErrors { migrate() }
    }

    private fun migrate(): Unit = logger.time("Migrate old benis record data") {
        val context = ApplicationClass.appContext

        val singleShotService: SingleShotService = context.injector.instance()
        if (!singleShotService.isFirstTime("migrate:benis-records")) {
            logger.debug { "No migration needed." }
            return
        }

        try {
            migrateNow(context)
            singleShotService.markAsDoneOnce("migrate:benis-records")

        } catch (err: SQLiteException) {
            if ("no such table" in err.message ?: "") {
                singleShotService.markAsDoneOnce("migrate:benis-records")
                logger.info { "No 'benis_record' table found, skipping migration." }
                return
            }

            logger.warn { "Ignoring error during migration: $err" }
        }
    }

    private fun migrateNow(context: Context) {
        val sqlOpenHelper = object : SQLiteOpenHelper(context, "pr0gramm.db", null, 12) {
            override fun onCreate(db: SQLiteDatabase) = Unit
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        sqlOpenHelper.writableDatabase.use { sql ->
            var recordsCount = 0

            db.transaction {
                val query = "SELECT benis, time, owner_id FROM benis_record"
                sql.rawQuery(query, emptyArray()).use { cursor: Cursor ->
                    while (cursor.moveToNext()) {
                        val score = cursor.getInt(0)
                        val time = cursor.getLong(1)
                        val ownerId = cursor.getInt(2)

                        recordsCount++
                        db.save(time, score, ownerId)
                    }
                }
            }

            logger.info { "Migrated $recordsCount entries, dropping old table now." }

            sql.execSQL("DROP TABLE benis_record")
        }
    }
}