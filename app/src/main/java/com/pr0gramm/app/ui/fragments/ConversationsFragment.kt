package com.pr0gramm.app.ui.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
import com.pr0gramm.app.ui.views.UsernameView
import com.pr0gramm.app.ui.views.ViewUpdater
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.instance
import kotlinx.coroutines.launch

/**
 */
class ConversationsFragment : BaseFragment("ConversationsFragment") {
    private val inboxService: InboxService by instance()

    private val swipeRefreshLayout: SwipeRefreshLayout by bindView(R.id.refresh)
    private val listView: RecyclerView by bindView(R.id.conversations)

    private var state by observeChange(State()) { updateAdapterValues() }

    private lateinit var adapter: ConversationsAdapter
    private lateinit var pagination: Pagination<Api.Conversation>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pagination = Pagination(this, ConversationsLoader(inboxService))
        adapter = ConversationsAdapter(PaginationController(pagination))
    }

    private fun applyPaginationUpdate(update: Pagination.Update<Api.Conversation>) {
        val mergedConversations = if (update.newValues.isNotEmpty()) {
            // only add conversations that are not yet in the list of conversations
            (state.conversations + update.newValues).distinctBy { it.name }
        } else {
            state.conversations
        }

        state = state.copy(conversations = mergedConversations, tailState = update.state.tailState)
    }

    private fun updateAdapterValues() {
        val values = state.conversations.toMutableList<Any>()
        addEndStateToValues(requireContext(), values, state.tailState)
        adapter.submitList(values)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_conversations, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listView.itemAnimator = null
        listView.layoutManager = LinearLayoutManager(activity)
        listView.adapter = adapter
        listView.addItemDecoration(SpacingItemDecoration(dp = 8))
        listView.addItemDecoration(MarginDividerItemDecoration(requireContext(), marginLeftDp = 72))

        swipeRefreshLayout.setOnRefreshListener { reloadConversations() }
        swipeRefreshLayout.setColorSchemeResources(ThemeHelper.accentColor)

        reloadConversations()
    }

    override fun onResume() {
        super.onResume()
        updateConversationsUnreadCount()

        pagination.updates.bindToLifecycle().subscribe { applyPaginationUpdate(it) }
    }

    private fun reloadConversations() {
        swipeRefreshLayout.isRefreshing = false

        // reset state and re-start pagination
        state = State()
        pagination.initialize()
    }

    private fun handleConversationClicked(conversation: Api.Conversation) {
        val context = context ?: return
        ConversationActivity.start(context, conversation.name, skipInbox = true)
        markConversationAsRead(conversation)
    }

    private fun markConversationAsRead(conversation: Api.Conversation) {
        val updatedConversations = state.conversations.map {
            if (it.name == conversation.name) it.copy(unreadCount = 0) else it
        }

        state = state.copy(conversations = updatedConversations)
    }

    private fun updateConversationsUnreadCount() {
        launch {
            catchAll {
                // get the most recent conversations, and replace them in the
                // pagination with their updated state.
                val response = inboxService.listConversations()
                val merged = state.conversations + response.conversations

                state = state.copy(conversations = merged.distinctBy { it.name })
            }
        }
    }

    inner class ConversationsAdapter(private val paginationController: PaginationController)
        : DelegateAdapter<Any>(ConversationsItemDiffCallback()) {

        init {
            delegates += ConversationAdapterDelegate(requireContext()) { handleConversationClicked(it) }
            delegates += ErrorAdapterDelegate()
            delegates += staticLayoutAdapterDelegate<Loading>(R.layout.feed_hint_loading)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            paginationController.hit(position, items.size)
            super.onBindViewHolder(holder, position)
        }
    }

    data class State(
            val conversations: List<Api.Conversation> = listOf(),
            val tailState: Pagination.EndState<Api.Conversation> = Pagination.EndState(hasMore = true))
}

private class ConversationsItemDiffCallback : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        return when {
            oldItem is Api.Conversation && newItem is Api.Conversation ->
                oldItem.name == newItem.name

            else ->
                newItem == oldItem
        }
    }

    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        return oldItem == newItem
    }

}

private class ConversationsLoader(private val inboxService: InboxService) : Pagination.Loader<Api.Conversation>() {
    override suspend fun loadAfter(currentValue: Api.Conversation?): Pagination.Page<Api.Conversation> {
        val olderThan = currentValue?.lastMessage
        val response = inboxService.listConversations(olderThan)
        return Pagination.Page.atTail(response.conversations, hasMore = !response.atEnd)
    }
}

private class ConversationAdapterDelegate(
        context: Context,
        private val conversationClicked: (Api.Conversation) -> Unit)
    : ListItemTypeAdapterDelegate<Api.Conversation, ConversationAdapterDelegate.ViewHolder>() {

    private val userIconService = UserDrawables(context)

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(parent.inflateDetachedChild(R.layout.item_conversation))
    }

    override fun onBindViewHolder(holder: ViewHolder, value: Api.Conversation) {
        holder.name.setUsername(value.name, value.mark)
        holder.image.setImageDrawable(userIconService.drawable(value.name))
        holder.itemView.setOnClickListener { conversationClicked(value) }

        holder.unreadCount.visible = value.unreadCount > 0
        holder.unreadCount.text = value.unreadCount.toString()

        ViewUpdater.replaceText(holder.date, value.lastMessage) {
            DurationFormat.timeSincePastPointInTime(
                    holder.itemView.context, value.lastMessage, short = true)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name = find<UsernameView>(R.id.name)
        val image = find<ImageView>(R.id.image)
        val date = find<TextView>(R.id.date)
        val unreadCount = find<TextView>(R.id.unread_count)
    }
}


