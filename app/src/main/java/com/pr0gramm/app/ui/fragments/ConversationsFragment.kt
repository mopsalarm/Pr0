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
import org.kodein.di.erased.instance

/**
 */
class ConversationsFragment : BaseFragment("ConversationsFragment") {
    private val inboxService: InboxService by instance()

    private val swipeRefreshLayout: SwipeRefreshLayout by bindView(R.id.refresh)
    private val listView: RecyclerView by bindView(R.id.conversations)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_conversations, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(listView) {
            itemAnimator = null
            layoutManager = LinearLayoutManager(activity)
            addItemDecoration(SpacingItemDecoration(dp = 8))
        }

        swipeRefreshLayout.setOnRefreshListener { reloadConversations() }
        swipeRefreshLayout.setColorSchemeResources(ThemeHelper.accentColor)

        reloadConversations()
    }

    private fun reloadConversations() {
        swipeRefreshLayout.isRefreshing = false
        listView.adapter = ConversationsAdapter()
    }

    private fun makeConversationsPagination(): Pagination<Api.Conversation> {
        return Pagination(this, ConversationsLoader(inboxService), Pagination.State.hasMoreState())
    }

    inner class ConversationsAdapter : PaginationRecyclerViewAdapter<Api.Conversation, Any>(
            makeConversationsPagination(),
            ConversationsItemDiffCallback()) {

        init {
            delegates += ConversationAdapterDelegate(requireContext()) { handleConversationClicked(it) }
            delegates += ErrorAdapterDelegate()
            delegates += staticLayoutAdapterDelegate<Loading>(R.layout.feed_hint_loading)
        }

        override fun translateState(state: Pagination.State<Api.Conversation>): List<Any> {
            val values = state.values.toMutableList<Any>()

            if (state.tailState.error != null) {
                values += LoadError(state.tailState.error.toString())
            }

            if (state.tailState.loading) {
                values += Loading()
            }

            return values
        }
    }

    private fun handleConversationClicked(conversation: Api.Conversation) {
        val context = context
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

private class ConversationsLoader(private val inboxService: InboxService) : Pagination.Loader<Api.Conversation>() {
    override suspend fun loadAfter(currentValues: List<Api.Conversation>): StateTransform<Api.Conversation> {
        val olderThan = currentValues.lastOrNull()?.lastMessage

        val response = inboxService.listConversations(olderThan)
        return { state ->
            state.copy(
                    values = (state.values + response.conversations).distinctBy { it.name },
                    tailState = state.tailState.copy(hasMore = !response.atEnd))
        }
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

