package com.pr0gramm.app.ui


import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasSize
import com.natpryce.hamkrest.isA
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.pr0gramm.app.feed.Feed
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.feed.FeedManager
import com.pr0gramm.app.feed.FeedService
import org.junit.Before
import org.junit.Test
import rx.observers.TestSubscriber
import rx.plugins.RxJavaHooks
import rx.schedulers.Schedulers

class FeedManagerTest {
    @Before
    fun resetRxScheduler() {
        RxJavaHooks.setOnIOScheduler { Schedulers.immediate() }
        RxJavaHooks.setOnComputationScheduler { Schedulers.immediate() }
    }

    @Test
    fun reload(): Unit {
        val service = mock<FeedService>() {
            on { load(any()) } doReturnObservable apiFeed {
                addItems(apiItem(9), apiItem(8), apiItem(3))
            }

            on { load(any()) } doReturnObservable apiFeed {
                addItems(apiItem(5), apiItem(3), apiItem(1))
            }
        }

        val testSubscriber = TestSubscriber<FeedManager.Update>()
        val manager = FeedManager(service, Feed())
        manager.updates.subscribe(testSubscriber)

        manager.restart()

        testSubscriber.assertNoTerminalEvent()
        testSubscriber.assertValueCount(4)

        val values = testSubscriber.onNextEvents
        assert.that(values[0], isA<FeedManager.Update.NewFeed>())
        assert.that(values[1], isA<FeedManager.Update.LoadingStarted>())
        assert.that(values[2], isA<FeedManager.Update.NewFeed>())
        assert.that(values[3], isA<FeedManager.Update.LoadingStopped>())

        val firstFeed = values[2] as FeedManager.Update.NewFeed
        assert.that(firstFeed.feed, hasSize(equalTo(3)))

        manager.next()

        testSubscriber.assertValueCount(7)
        assert.that(values[4], isA<FeedManager.Update.LoadingStarted>())
        assert.that(values[5], isA<FeedManager.Update.NewFeed>())
        assert.that(values[6], isA<FeedManager.Update.LoadingStopped>())

        val secondFeed = values[5] as FeedManager.Update.NewFeed
        assert.that(secondFeed.feed, hasSize(equalTo(6)))
    }


    @Test
    fun previousAllowed(): Unit {
        val service = mock<FeedService>() {
            on { load(any()) } doReturnObservable apiFeed()
        }

        val manager = FeedManager(service, Feed(isAtStart = false, items = listOf(FeedItem(apiItem(5)))))
        manager.previous()

        verify(service).load(any())
    }

    @Test
    fun previousNotAllowed(): Unit {
        val service = mock<FeedService>()

        val manager = FeedManager(service, Feed(isAtStart = true, items = listOf(FeedItem(apiItem(5)))))
        manager.previous()

        verifyZeroInteractions(service)
    }

}
