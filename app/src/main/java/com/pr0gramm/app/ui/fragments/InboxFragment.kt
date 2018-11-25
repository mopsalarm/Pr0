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
import com.pr0gramm.app.ui.InboxType
import com.pr0gramm.app.ui.MainActivity
import com.pr0gramm.app.ui.MessageActionListener
import com.pr0gramm.app.ui.WriteMessageActivity
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindView
import com.pr0gramm.app.util.visible
import org.kodein.di.erased.instance
import java.util.concurrent.TimeUnit

/**
 */
abstract class InboxFragment<T>(name: String) : BaseFragment(name) {
    protected val inboxService: InboxService by instance()

    private val viewNothingHere: View by bindView(android.R.id.empty)
    private val swipeRefreshLayout: SwipeRefreshLayout by bindView(R.id.refresh)
    private val messagesView: RecyclerView by bindView(R.id.messages)
    private val viewBusyIndicator: View by bindView(R.id.busy_indicator)

    private var loadStartedTimestamp = Instant(0)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_inbox, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(messagesView) {
            itemAnimator = null
            layoutManager = LinearLayoutManager(activity)
        }

        swipeRefreshLayout.setOnRefreshListener { reloadInboxContent() }
        swipeRefreshLayout.setColorSchemeResources(ThemeHelper.accentColor)

        showBusyIndicator()

        // load the messages
        reloadAsync()
    }

    override fun onResume() {
        super.onResume()

        // reload if re-started after one minute
        if (loadStartedTimestamp.plus(1, TimeUnit.MINUTES).isBeforeNow) {
            loadStartedTimestamp = Instant.now()
            reloadInboxContent()
        }
    }

    private fun reloadInboxContent() {
        reloadAsync()
    }

    private fun hideNothingHereIndicator() {
        viewNothingHere.visible = false
    }

    private fun showNothingHereIndicator() {
        viewNothingHere.visible = true
        viewNothingHere.alpha = 0f
        viewNothingHere.animate().alpha(1f).start()
    }

    private fun showBusyIndicator() {
        viewBusyIndicator.visible = true
    }

    private fun hideBusyIndicator() {
        viewBusyIndicator.visible = false
        swipeRefreshLayout.isRefreshing = false
    }

    private fun reloadAsync() {
        loadStartedTimestamp = Instant.now()

        showBusyIndicator()

        launchWithErrorHandler {
            try {
                val messages = loadContent()
                onMessagesLoaded(messages)
            } finally {
                if (job.isActive) {
                    hideBusyIndicator()
                }
            }
        }
    }

    private fun onMessagesLoaded(messages: List<T>) {
        hideBusyIndicator()
        hideNothingHereIndicator()

        // replace previous adapter with new values
        displayMessages(messagesView, messages)

        if (messages.isEmpty())
            showNothingHereIndicator()
    }

    protected abstract suspend fun loadContent(): List<T>

    protected abstract fun displayMessages(recyclerView: RecyclerView, messages: List<T>)

    protected val inboxType: InboxType get() {
        var type = InboxType.ALL
        val args = arguments
        if (args != null) {
            type = InboxType.values()[args.getInt(ARG_INBOX_TYPE, InboxType.ALL.ordinal)]
        }

        return type
    }

    protected val actionListener: MessageActionListener = object : MessageActionListener {
        override fun onAnswerToPrivateMessage(message: Api.Message) {
            startActivity(WriteMessageActivity.intent(context, message))
        }

        override fun onAnswerToCommentClicked(comment: Api.Message) {
            startActivity(WriteMessageActivity.answerToComment(context, comment))
        }

        override fun onNewPrivateMessage(userId: Long, name: String) {
            startActivity(WriteMessageActivity.intent(context, userId, name))
        }

        override fun onCommentClicked(comment: Api.Message) {
            val uri = UriHelper.of(context).post(FeedType.NEW, comment.itemId, comment.id)
            open(uri, comment.creationTime)
        }

        private fun open(uri: Uri, notificationTime: Instant? = null) {
            val intent = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java)
            intent.putExtra("MainActivity.NOTIFICATION_TIME", notificationTime)
            startActivity(intent)
        }

        override fun onUserClicked(userId: Int, username: String) {
            open(UriHelper.of(context).uploads(username))
        }
    }

    companion object {
        const val ARG_INBOX_TYPE = "InboxFragment.inboxType"
    }
}
