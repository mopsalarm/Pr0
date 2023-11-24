package com.pr0gramm.app

import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.pr0gramm.app.db.AppDB
import com.pr0gramm.app.db.CachedItemInfo
import com.pr0gramm.app.db.CachedVote
import com.pr0gramm.app.db.ScoreRecord
import com.pr0gramm.app.db.UserFollowEntry

fun buildAppDB(driver: AndroidSqliteDriver) = AppDB(
    driver,
    CachedItemInfoAdapter = CachedItemInfo.Adapter(
        widthAdapter = IntColumnAdapter,
        heightAdapter = IntColumnAdapter,
        upAdapter = IntColumnAdapter,
        downAdapter = IntColumnAdapter,
        markAdapter = IntColumnAdapter,
        flagsAdapter = IntColumnAdapter,
    ),
    cachedVoteAdapter = CachedVote.Adapter(
        itemTypeAdapter = IntColumnAdapter,
        voteValueAdapter = IntColumnAdapter,
    ),
    scoreRecordAdapter = ScoreRecord.Adapter(
        scoreAdapter = IntColumnAdapter,
        owner_idAdapter = IntColumnAdapter,
    ),
    userFollowEntryAdapter = UserFollowEntry.Adapter(
        stateAdapter = IntColumnAdapter,
    ),
)
