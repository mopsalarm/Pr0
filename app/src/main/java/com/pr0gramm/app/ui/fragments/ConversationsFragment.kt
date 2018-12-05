package com.pr0gramm.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.pr0gramm.app.ui.views.UsernameView
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.inflateDetachedChild
import org.kodein.di.erased.instance

/**
 */
abstract class ConversationsFragment : BaseFragment("ConversationsFragment") {
    protected val inboxService: InboxService by instance()

    private val swipeRefreshLayout: SwipeRefreshLayout by bindView(R.id.refresh)
    private val listView: RecyclerView by bindView(R.id.messages)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_conversations, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(listView) {
            itemAnimator = null
            layoutManager = LinearLayoutManager(activity)
        }

        swipeRefreshLayout.setOnRefreshListener { reloadConversations() }
        swipeRefreshLayout.setColorSchemeResources(ThemeHelper.accentColor)

        reloadConversations()
    }

    private fun reloadConversations() {
        val adapter = ConversationsAdapter()
        adapter.initialize()

        listView.adapter = adapter
    }

    private fun makeConversationsPagination(): Pagination<List<Api.Conversation>> {
        return Pagination(this, ConversationsLoader(inboxService), Pagination.State.hasMoreState())
    }

    inner class ConversationsAdapter : PaginationRecyclerViewAdapter<List<Api.Conversation>, Any>(
            makeConversationsPagination(),
            ConversationsItemDiffCallback()) {

        init {
            delegates += ConversationAdapterDelegate { handleConversationClicked(it) }
            delegates += ErrorAdapterDelegate()
            delegates += staticLayoutAdapterDelegate(R.layout.feed_hint_loading, Loading)
        }

        override fun updateAdapterValues(state: Pagination.State<List<Api.Conversation>>) {
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

    private fun handleConversationClicked(conversation: Api.Conversation) {
        val context = context ?: return
        ConversationActivity.start(context, conversation.name)
    }
}

data class LoadError(override val errorText: String) : ErrorAdapterDelegate.Value

private class ConversationsItemDiffCallback : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        return when {
            oldItem is Api.Conversation && newItem is Api.Conversation ->
                oldItem.name == newItem.name

            else ->
                newItem === oldItem
        }
    }

    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        return oldItem == newItem
    }

}

private class ConversationsLoader(private val inboxService: InboxService) : Pagination.Loader<List<Api.Conversation>>() {
    override suspend fun loadAfter(currentValue: List<Api.Conversation>): StateTransform<List<Api.Conversation>> {
        val olderThan = currentValue.lastOrNull()?.lastMessage

        val response = inboxService.listConversations(olderThan)
        return { state ->
            state.copy(
                    value = state.value + response.conversations,
                    valueCount = state.valueCount + response.conversations.size,
                    tailState = state.tailState.copy(hasMore = !response.atEnd))
        }
    }
}

private class ConversationAdapterDelegate(private val conversationClicked: (Api.Conversation) -> Unit)
    : ListItemTypeAdapterDelegate<Api.Conversation, ConversationAdapterDelegate.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(parent.inflateDetachedChild(R.layout.item_conversation))
    }

    override fun onBindViewHolder(holder: ViewHolder, value: Api.Conversation) {
        holder.name.setUsername(value.name, value.mark)
        holder.itemView.setOnClickListener { conversationClicked(value) }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name = find<UsernameView>(R.id.name)
    }
}
