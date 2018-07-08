package com.pr0gramm.app.orm

import android.annotation.SuppressLint
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.pr0gramm.app.util.forEach
import com.pr0gramm.app.util.time
import com.squareup.sqlbrite.BriteDatabase
import org.slf4j.LoggerFactory
import rx.Observable

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

        fun find(db: BriteDatabase, type: Type, itemId: Long): Observable<CachedVote> {
            val query = "SELECT item_id, type, vote FROM cached_vote WHERE id=?"
            return db
                    .createQuery("cached_vote", query, voteId(type, itemId).toString())
                    .mapToOneOrDefault(this::ofCursor, CachedVote(itemId, type, Vote.NEUTRAL))
        }

        private fun ofCursor(cursor: Cursor): CachedVote {
            return CachedVote(itemId = cursor.getLong(0),
                    type = Type.valueOf(cursor.getString(1)),
                    vote = Vote.valueOf(cursor.getString(2)))
        }

        fun find(db: BriteDatabase, type: Type, ids: List<Long>): Observable<List<CachedVote>> {
            if (ids.isEmpty())
                return Observable.just(emptyList())

            val encodedIds = StringBuilder().apply {
                append(voteId(type, ids[0]))

                for (index in 1 until ids.size) {
                    append(',')
                    append(voteId(type, ids[index]))
                }

                toString()
            }

            val query = "SELECT item_id, type, vote FROM cached_vote WHERE id IN ($encodedIds)"
            return db.createQuery("cached_vote", query).mapToList(this::ofCursor)
        }

        fun quickSave(database: BriteDatabase, type: Type, itemId: Long, vote: Vote) {
            database.executeAndTrigger("cached_vote", """
                INSERT OR REPLACE INTO cached_vote (id, item_id, type, vote)
                VALUES (${voteId(type, itemId)}, $itemId, "${type.name}", "${vote.name}")
            """)
        }

        fun clear(database: BriteDatabase) {
            database.executeAndTrigger("cached_vote", "DELETE FROM cached_vote")
        }

        fun prepareDatabase(db: SQLiteDatabase) {
            logger.info("Create cached_vote table if it does not exist.")
            db.execSQL("CREATE TABLE IF NOT EXISTS cached_vote (id INTEGER PRIMARY KEY, type TEXT, vote TEXT, item_id INTEGER)")
        }

        @SuppressLint("Recycle")
        fun count(db: SQLiteDatabase): Map<Type, Map<Vote, Int>> {
            val result = HashMap<Type, HashMap<Vote, Int>>()

            logger.time("Counting number of votes") {
                val cursor = db.rawQuery("SELECT type, vote, COUNT(*) FROM cached_vote GROUP BY type, vote", emptyArray())
                cursor.forEach {
                    val type = Type.valueOf(getString(0))
                    val vote = Vote.valueOf(getString(1))
                    val count = getInt(2)

                    val votes = result.getOrPut(type, { HashMap() })
                    votes[vote] = (votes[vote] ?: 0) + count
                }
            }

            return result
        }
    }
}
