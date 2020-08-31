package com.pr0gramm.app.ui.fragments.pager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pr0gramm.app.feed.Feed
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.feed.FeedManager
import com.pr0gramm.app.feed.FeedService
import com.pr0gramm.app.ui.fragments.feed.update
import com.pr0gramm.app.util.trace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class PostPagerViewModel(feed: Feed, feedService: FeedService, initialItem: FeedItem) : ViewModel() {
    private val mutableState = MutableStateFlow(State(feed))
    val state: StateFlow<State> = mutableState

    var currentItem: FeedItem = initialItem

    // initialize loader based on the input feed
    private val loader = FeedManager(viewModelScope, feedService, feed)

    init {
        viewModelScope.launch { observeFeedUpdates() }
    }

    private suspend fun observeFeedUpdates() {
        loader.updates.collect { update ->
            trace { "observeFeedUpdates($update)" }

            if (update is FeedManager.Update.NewFeed) {
                mutableState.update { previousState ->
                    previousState.copy(feed = update.feed)
                }
            }
        }
    }

    fun triggerLoadNext() {
        loader.next()
    }

    fun triggerLoadPrev() {
        loader.previous()
    }

    data class State(
            val feed: Feed,
    )
}