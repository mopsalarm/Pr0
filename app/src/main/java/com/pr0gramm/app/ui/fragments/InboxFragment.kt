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
import com.pr0gramm.app.ui.MainActivity
import com.pr0gramm.app.ui.MessageActionListener
import com.pr0gramm.app.ui.WriteMessageActivity
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindView
import org.kodein.di.erased.instance
import java.util.concurrent.TimeUnit

/**
 */
abstract class InboxFragment(name: String) : BaseFragment(name) {
    protected val inboxService: InboxService by instance()

    private val swipeRefreshLayout: SwipeRefreshLayout by bindView(R.id.refresh)
    private val messagesView: RecyclerView by bindView(R.id.messages)

    private var loadStartedTimestamp = Instant(0)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_inbox, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(messagesView) {
            itemAnimator = null
            layoutManager = LinearLayoutManager(activity)
            adapter = getContentAdapter()
        }

        swipeRefreshLayout.setOnRefreshListener { reloadInboxContent() }
        swipeRefreshLayout.setColorSchemeResources(ThemeHelper.accentColor)
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
        // TODO
    }

    abstract fun getContentAdapter(): RecyclerView.Adapter<*>

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
}
