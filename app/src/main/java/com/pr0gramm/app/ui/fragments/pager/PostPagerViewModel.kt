package com.pr0gramm.app.ui.fragments.pager

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pr0gramm.app.feed.Feed
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.feed.FeedManager
import com.pr0gramm.app.feed.FeedService
import com.pr0gramm.app.ui.SavedStateAccessor
import com.pr0gramm.app.ui.fragments.feed.update
import com.pr0gramm.app.util.observeChangeEx
import com.pr0gramm.app.util.trace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PostPagerViewModel(private val savedState: SavedState, feedService: FeedService) : ViewModel() {
    private val mutableState = MutableStateFlow(State(savedState.fp.feed.withoutPlaceholderItems()))
    val state: StateFlow<State> = mutableState

    var currentItem: FeedItem by observeChangeEx(savedState.currentItem) { _, newValue ->
        savedState.currentItem = newValue
        savedState.fp = mutableState.value.feed.parcelAroundId(newValue.id)
    }

    // initialize loader based on the input feed
    private val loader = FeedManager(viewModelScope, feedService, mutableState.value.feed)

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

    class SavedState(handle: SavedStateHandle, feed: Feed, initialItem: FeedItem) : SavedStateAccessor(handle) {
        var currentItem: FeedItem by savedStateValue("currentItem", initialItem)
        var fp: Feed.FeedParcel by savedStateValue("feed", feed.parcelAroundId(initialItem.id))
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

