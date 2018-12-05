package com.pr0gramm.app.ui.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.InboxService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.*
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindView
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.fragmentArgument
import com.pr0gramm.app.util.inflateDetachedChild
import org.kodein.di.erased.instance

/**
 */
class ConversationFragment : BaseFragment("ConversationFragment") {
    protected val inboxService: InboxService by instance()

    private val swipeRefreshLayout: SwipeRefreshLayout by bindView(R.id.refresh)
    private val listView: RecyclerView by bindView(R.id.messages)

    var conversationName: String by fragmentArgument()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_conversation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(listView) {
            itemAnimator = null
            layoutManager = LinearLayoutManager(activity)
        }

        swipeRefreshLayout.setOnRefreshListener { reloadConversation() }
        swipeRefreshLayout.setColorSchemeResources(ThemeHelper.accentColor)

        setTitle(conversationName)

        reloadConversation()
    }

    private fun reloadConversation() {
        val adapter = ConversationAdapter()
        adapter.initialize()

        listView.adapter = adapter
    }

    private fun makeConversationsPagination(): Pagination<List<Api.ConversationMessage>> {
        return Pagination(this, ConversationLoader(conversationName, inboxService), Pagination.State.hasMoreState())
    }

    inner class ConversationAdapter : PaginationRecyclerViewAdapter<List<Api.ConversationMessage>, Any>(
            makeConversationsPagination(),
            ConversationItemDiffCallback()) {

        init {
            delegates += MessageAdapterDelegate(sentValue = true)
            delegates += MessageAdapterDelegate(sentValue = false)
            delegates += staticLayoutAdapterDelegate(R.layout.feed_hint_loading, Loading)
            delegates += ErrorAdapterDelegate()
        }

        override fun updateAdapterValues(state: Pagination.State<List<Api.ConversationMessage>>) {
            val values = state.value.mapTo(mutableListOf<Any>()) { it }

            if (state.tailState.error != null) {
                values += LoadError(state.tailState.error.toString())
            }

            if (state.tailState.loading) {
                values += Loading
            }

            submitList(values)
        }
    }
}

private class ConversationItemDiffCallback : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        return when {
            oldItem is Api.ConversationMessage && newItem is Api.ConversationMessage ->
                oldItem.id == newItem.id

            else ->
                newItem === oldItem
        }
    }

    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        return oldItem == newItem
    }

}

private class ConversationLoader(private val name: String, private val inboxService: InboxService) : Pagination.Loader<List<Api.ConversationMessage>>() {
    override suspend fun loadAfter(currentValue: List<Api.ConversationMessage>): StateTransform<List<Api.ConversationMessage>> {
        val olderThan = currentValue.lastOrNull()?.creationTime

        val response = inboxService.messagesInConversation(name, olderThan)
        return { state ->
            state.copy(
                    value = state.value + response.messages,
                    valueCount = state.valueCount + response.messages.size,
                    tailState = state.tailState.copy(hasMore = !response.atEnd))
        }
    }
}

private class MessageAdapterDelegate(private val sentValue: Boolean)
    : ItemAdapterDelegate<Api.ConversationMessage, Any, ConversationMessageViewHolder>() {

    override fun isForViewType(value: Any): Boolean {
        return value is Api.ConversationMessage && value.sent == sentValue
    }

    override fun onCreateViewHolder(parent: ViewGroup): ConversationMessageViewHolder {
        val layout = if (sentValue) R.layout.item_message_sent else R.layout.item_message_received
        return ConversationMessageViewHolder(parent.inflateDetachedChild(layout))
    }

    override fun onBindViewHolder(holder: ConversationMessageViewHolder, value: Api.ConversationMessage) {
        holder.message.text = value.message
    }
}

private class ConversationMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val message = find<TextView>(R.id.message)
}

