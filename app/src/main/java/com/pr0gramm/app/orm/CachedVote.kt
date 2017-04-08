package com.pr0gramm.app.orm

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.google.common.base.Optional
import com.pr0gramm.app.util.mapToList
import org.slf4j.LoggerFactory

/**
 * A cached vote.
 */
data class CachedVote(val itemId: Long, val type: CachedVote.Type, val vote: Vote) {
    enum class Type {
        ITEM, COMMENT, TAG
    }

    companion object {
        private val logger = LoggerFactory.getLogger("CachedVote")

        private fun voteId(type: Type, itemId: Long): Long {
            return itemId * 10 + type.ordinal
        }

        fun find(db: SQLiteDatabase, type: Type, itemId: Long): Optional<CachedVote> {
            val query = "SELECT item_id, type, vote FROM cached_vote WHERE id=?"
            db.rawQuery(query, arrayOf(voteId(type, itemId).toString())).use { cursor ->
                return if (cursor.moveToNext()) {
                    Optional.of(ofCursor(cursor))
                } else {
                    Optional.absent<CachedVote>()
                }
            }
        }

        private fun ofCursor(cursor: Cursor): CachedVote {
            return CachedVote(itemId = cursor.getLong(0),
                    type = Type.valueOf(cursor.getString(1)),
                    vote = Vote.valueOf(cursor.getString(2)))
        }

        fun find(db: SQLiteDatabase, type: Type, ids: List<Long>): List<CachedVote> {
            if (ids.isEmpty())
                return emptyList()

            val encodedIds = ids.sorted().map { voteId(type, it) }.joinToString(",")
            val query = "SELECT item_id, type, vote FROM cached_vote WHERE id IN ($encodedIds)"
            return db.rawQuery(query, emptyArray()).use { cursor ->
                cursor.mapToList { ofCursor(this) }
            }
        }

        fun quickSave(database: SQLiteDatabase, type: Type, itemId: Long, vote: Vote) {
            database.execSQL("""
                INSERT OR REPLACE INTO cached_vote (id, item_id, type, vote)
                VALUES (${voteId(type, itemId)}, $itemId, "${type.name}", "${vote.name}")
            """)
        }

        fun clear(database: SQLiteDatabase) {
            database.execSQL("DELETE FROM cached_vote")
        }

        fun prepareDatabase(db: SQLiteDatabase) {
            logger.info("Create cached_vote table if it does not exist.")
            db.execSQL("CREATE TABLE IF NOT EXISTS cached_vote (id INTEGER PRIMARY KEY, type TEXT, vote TEXT, item_id INTEGER)")
        }
    }
}
