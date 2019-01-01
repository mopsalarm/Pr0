package com.pr0gramm.app.services

import android.database.sqlite.SQLiteDatabase
import com.pr0gramm.app.Instant
import com.pr0gramm.app.TimeFactory
import com.pr0gramm.app.orm.BenisRecord
import com.pr0gramm.app.util.Holder
import com.pr0gramm.app.util.arrayOfStrings
import com.pr0gramm.app.util.checkNotMainThread
import com.pr0gramm.app.util.mapToList

class BenisRecordService(private val database: Holder<SQLiteDatabase>) {
    fun findValuesLaterThan(ownerId: Int, time: Instant): List<BenisRecord> {
        checkNotMainThread()

        val db = database.value

        val columns = arrayOf("time", "benis")

        val cursor = db.query("benis_record", columns,
                "time >= ? and owner_id=?", arrayOfStrings(time.millis, ownerId),
                null, null, "time ASC")

        return cursor.mapToList { BenisRecord(getLong(0), getInt(1)) }
    }

    fun storeValue(ownerId: Int, benis: Int) {
        checkNotMainThread()

        val sql = "INSERT INTO benis_record (owner_id, time, benis) VALUES (?, ?, ?)"
        database.value.execSQL(sql, arrayOfStrings(ownerId, TimeFactory.currentTimeMillis(), benis))
    }

    companion object {
        fun prepare(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS benis_record (id INTEGER PRIMARY KEY AUTOINCREMENT, benis INTEGER, time INTEGER, owner_id INTEGER)")
            db.execSQL("CREATE INDEX IF NOT EXISTS benis_record__time ON benis_record(time)")
        }
    }
}