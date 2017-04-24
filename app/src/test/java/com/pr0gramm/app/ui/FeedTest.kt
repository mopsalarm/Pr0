package com.pr0gramm.app.ui


import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasSize
import com.pr0gramm.app.feed.Feed
import com.pr0gramm.app.feed.FeedItem
import org.junit.Test

class FeedTest {

    @Test
    fun feedMergeOnEmpty() {
        val feed = Feed()
        val merged = feed.mergeWith(apiFeed {
            addItems(apiItem(10), apiItem(9), apiItem(8))
        })

        assert.that(merged, hasSize(equalTo(3)))
        assert.that(merged[0].id, equalTo(10L))
        assert.that(merged[1].id, equalTo(9L))
        assert.that(merged[2].id, equalTo(8L))
    }

    @Test
    fun feedMergeOlderPage() {
        val feed = Feed(items = listOf(apiItem(10), apiItem(9), apiItem(8)).map(::FeedItem))

        val merged = feed.mergeWith(apiFeed {
            addItems(apiItem(5), apiItem(4), apiItem(3))
        })

        assert.that(merged, hasSize(equalTo(6)))
        assert.that(merged.map { it.id }, equalTo(listOf<Long>(10, 9, 8, 5, 4, 3)))
    }

    @Test
    fun feedMergeNewerPage() {
        val feed = Feed(items = listOf(apiItem(5), apiItem(4), apiItem(3)).map(::FeedItem))

        val merged = feed.mergeWith(apiFeed {
            addItems(apiItem(10), apiItem(9), apiItem(8))
        })

        assert.that(merged, hasSize(equalTo(6)))
        assert.that(merged.map { it.id }, equalTo(listOf<Long>(10, 9, 8, 5, 4, 3)))
    }

    @Test
    fun feedMergeInterleaved() {
        val feed = Feed(items = listOf(apiItem(10), apiItem(5), apiItem(4)).map(::FeedItem))

        val merged = feed.mergeWith(apiFeed {
            addItems(apiItem(9), apiItem(8), apiItem(3))
        })

        assert.that(merged, hasSize(equalTo(6)))
        assert.that(merged.map { it.id }, equalTo(listOf<Long>(10, 9, 8, 5, 4, 3)))
    }
}
