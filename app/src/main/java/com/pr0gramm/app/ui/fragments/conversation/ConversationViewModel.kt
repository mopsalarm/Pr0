package com.pr0gramm.app.ui.fragments.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.InboxService
import com.pr0gramm.app.util.StringException
import com.pr0gramm.app.util.lazyStateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import java.io.IOException

class ConversationViewModel(private val inboxService: InboxService, private val recipient: String) : ViewModel() {
    private var currentPagingSource = lazyStateFlow<ConversationSource>()
    private var firstPageConversationMessages: Api.ConversationMessages? = null

    private val pager = Pager(PagingConfig(pageSize = 75, enablePlaceholders = false)) {
        ConversationSource(inboxService, recipient, firstPageConversationMessages).also { source ->
            currentPagingSource.send(source)
            firstPageConversationMessages = null
        }
    }

    val partner = currentPagingSource.flatMapLatest { source -> source.partner }

    val paging by lazy { pager.flow.cachedIn(viewModelScope) }

    var pendingMessages = MutableStateFlow(listOf<String>())

    suspend fun send(messageText: String) {
        // publish message as "pending"
        pendingMessages.value = pendingMessages.value + messageText
        try {

            val response = withContext(NonCancellable) {
                inboxService.send(recipient, messageText)
            }

            firstPageConversationMessages = response

        } finally {
            // invalidate even in case of errors
            currentPagingSource.valueOrNull?.invalidate()

            // remove the pending message "by instance"
            pendingMessages.value = pendingMessages.value.filterNot { it === messageText }
        }
    }

    suspend fun delete(name: String) {
        inboxService.deleteConversation(name)
    }
}

class ConversationSource(
        private val inboxService: InboxService, private val name: String,
        private var pending: Api.ConversationMessages?) : PagingSource<Instant, Api.ConversationMessage>() {

    val partner = lazyStateFlow<Api.ConversationMessages.ConversationMessagePartner>()

    override suspend fun load(params: LoadParams<Instant>): LoadResult<Instant, Api.ConversationMessage> {
        val olderThan = params.key

        try {
            val response = pending ?: try {
                inboxService.messagesInConversation(name, olderThan)
            } catch (err: Exception) {
                return LoadResult.Error(err)
            }

            if (response.error != null) {
                return LoadResult.Error(StringException("load", R.string.conversation_load_error))
            }

            // It should never be the case that 'response.with' is null as we are only fetching
            // messages
            response.with?.let {
                partner.send(it)
            }

            pending = null

            val nextKey = if (response.atEnd) null else response.messages.lastOrNull()?.creationTime
            return LoadResult.Page(response.messages.reversed(), nextKey = null, prevKey = nextKey)

        } catch (err: IOException) {
            return LoadResult.Error(err)
        }
    }

    override fun getRefreshKey(state: PagingState<Instant, Api.ConversationMessage>): Instant? {
        return null
    }
}
