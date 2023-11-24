package com.pr0gramm.app.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.databinding.FragmentInboxBinding
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.services.InboxService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.ConversationActivity
import com.pr0gramm.app.ui.MainActivity
import com.pr0gramm.app.ui.MarginDividerItemDecoration
import com.pr0gramm.app.ui.MessageActionListener
import com.pr0gramm.app.ui.MessageAdapter
import com.pr0gramm.app.ui.Pagination
import com.pr0gramm.app.ui.WriteMessageActivity
import com.pr0gramm.app.ui.addEndStateToValues
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindViews
import com.pr0gramm.app.ui.fragments.conversation.StringValue
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.observeChange
import java.util.concurrent.TimeUnit

/**
 */
abstract class InboxFragment(name: String) : BaseFragment(name, R.layout.fragment_inbox) {
    protected val inboxService: InboxService by instance()

    private val views by bindViews(FragmentInboxBinding::bind)

    private var loadStartedTimestamp = Instant(0)

    private lateinit var adapter: MessageAdapter
    private lateinit var pagination: Pagination<Message>

    private var state by observeChange(State()) { updateAdapterValues() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (adapter, pagination) = getContentAdapter()
        this.adapter = adapter
        this.pagination = pagination
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        views.messages.itemAnimator = null
        views.messages.layoutManager = LinearLayoutManager(activity)
        views.messages.adapter = adapter
        views.messages.addItemDecoration(MarginDividerItemDecoration(requireContext(), marginLeftDp = 72))

        views.refresh.setOnRefreshListener { reloadInboxContent() }
        views.refresh.setColorSchemeResources(ThemeHelper.accentColor)

        pagination.updates.observe(viewLifecycleOwner) { (state, newValues) ->
            handleStateUpdate(state, newValues)
        }
    }

    override fun onResume() {
        super.onResume()

        // reload if re-started after one minute
        if (loadStartedTimestamp.plus(1, TimeUnit.MINUTES).isBefore(Instant.now())) {
            loadStartedTimestamp = Instant.now()
            reloadInboxContent()
        }
    }

    private fun reloadInboxContent() {
        views.refresh.isRefreshing = false

        // re-set state and re-start pagination
        state = State()
        pagination.initialize()
    }

    abstract fun getContentAdapter(): Pair<MessageAdapter, Pagination<Message>>

    private fun handleStateUpdate(state: Pagination.State<Message>, newValues: List<Message>) {
        this.state = this.state.copy(
                messages = (this.state.messages + newValues).distinctBy { msg -> msg.id },
                tailState = state.tailState)
    }

    private fun updateAdapterValues() {
        val context = context ?: return

        // build values for the recycler view
        val values = state.messages.toMutableList<Any>()

        // add the divider above the first read message, but only
        // if we have at least one unread message
        val dividerIndex = values.indexOfFirst { it is Message && it.read }
        if (dividerIndex > 0) {
            val divider = StringValue(context.getString(R.string.inbox_type_unread))
            values.add(dividerIndex, divider)
        }

        // add common state values
        addEndStateToValues(context, values, state.tailState, ifEmptyValue = MessageAdapter.EmptyValue)

        adapter.submitList(values)
    }

    protected val actionListener: MessageActionListener = object : MessageActionListener {
        override fun onAnswerToPrivateMessage(message: Message) {
            ConversationActivity.start(context ?: return, message.name, skipInbox = true)
        }

        override fun onAnswerToCommentClicked(comment: Message) {
            startActivity(WriteMessageActivity.answerToComment(context ?: return, comment))
        }

        override fun onNewPrivateMessage(userId: Long, name: String) {
            startActivity(WriteMessageActivity.intent(context ?: return, userId, name))
        }

        override fun onCommentClicked(comment: Message) {
            val uri = UriHelper.of(context ?: return).post(FeedType.NEW, comment.itemId, comment.id)
            open(uri, comment.creationTime)
        }

        private fun open(uri: Uri, notificationTime: Instant? = null) {
            val intent = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java)
            intent.putExtra("MainActivity.NOTIFICATION_TIME", notificationTime)
            startActivity(intent)
        }

        override fun onUserClicked(userId: Int, username: String) {
            open(UriHelper.of(context ?: return).uploads(username))
        }
    }

    data class State(
            val messages: List<Message> = listOf(),
            val tailState: Pagination.EndState<Message> = Pagination.EndState(hasMore = true))
}
