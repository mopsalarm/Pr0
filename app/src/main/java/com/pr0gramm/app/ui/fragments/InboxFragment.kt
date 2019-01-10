package com.pr0gramm.app.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.services.InboxService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.*
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindView
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.observeChange
import java.util.concurrent.TimeUnit

/**
 */
abstract class InboxFragment(name: String) : BaseFragment(name) {
    protected val inboxService: InboxService by instance()

    private val swipeRefreshLayout: SwipeRefreshLayout by bindView(R.id.refresh)
    private val messagesView: RecyclerView by bindView(R.id.messages)

    private var loadStartedTimestamp = Instant(0)

    private lateinit var adapter: MessageAdapter
    private lateinit var pagination: Pagination<Api.Message>

    private var state by observeChange(State()) { updateAdapterValues() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (adapter, pagination) = getContentAdapter()
        this.adapter = adapter
        this.pagination = pagination
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_inbox, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        messagesView.itemAnimator = null
        messagesView.layoutManager = LinearLayoutManager(activity)
        messagesView.adapter = adapter
        messagesView.addItemDecoration(MarginDividerItemDecoration(requireContext(), marginLeftDp = 72))

        swipeRefreshLayout.setOnRefreshListener { reloadInboxContent() }
        swipeRefreshLayout.setColorSchemeResources(ThemeHelper.accentColor)
    }

    override suspend fun onResumeImpl() {
        pagination.updates.bindToLifecycle().subscribe { (state, newValues) ->
            handleStateUpdate(state, newValues)
        }

        // reload if re-started after one minute
        if (loadStartedTimestamp.plus(1, TimeUnit.MINUTES).isInPast) {
            loadStartedTimestamp = Instant.now()
            reloadInboxContent()
        }
    }

    private fun reloadInboxContent() {
        swipeRefreshLayout.isRefreshing = false

        // re-set state and re-start pagination
        state = State()
        pagination.initialize()
    }

    abstract fun getContentAdapter(): Pair<MessageAdapter, Pagination<Api.Message>>

    private fun handleStateUpdate(state: Pagination.State<Api.Message>, newValues: List<Api.Message>) {
        this.state = this.state.copy(
                messages = this.state.messages + newValues,
                tailState = state.tailState)
    }

    private fun updateAdapterValues() {
        val context = context ?: return

        // build values for the recycler view
        val values = state.messages.toMutableList<Any>()

        // add the divider above the first read message, but only
        // if we have at least one unread message
        val dividerIndex = values.indexOfFirst { it is Api.Message && it.read }
        if (dividerIndex > 0) {
            val divider = DividerAdapterDelegate.Value(context.getString(R.string.inbox_type_unread))
            values.add(dividerIndex, divider)
        }

        // add common state values
        addEndStateToValues(context, values, state.tailState, ifEmptyValue = MessageAdapter.EmptyValue)

        adapter.submitList(values)
    }

    protected val actionListener: MessageActionListener = object : MessageActionListener {
        override fun onAnswerToPrivateMessage(message: Api.Message) {
            startActivity(WriteMessageActivity.intent(context ?: return, message))
        }

        override fun onAnswerToCommentClicked(comment: Api.Message) {
            startActivity(WriteMessageActivity.answerToComment(context ?: return, comment))
        }

        override fun onNewPrivateMessage(userId: Long, name: String) {
            startActivity(WriteMessageActivity.intent(context ?: return, userId, name))
        }

        override fun onCommentClicked(comment: Api.Message) {
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
            val messages: List<Api.Message> = listOf(),
            val tailState: Pagination.EndState<Api.Message> = Pagination.EndState(hasMore = true))
}
