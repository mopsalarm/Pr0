package com.pr0gramm.app.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.defaultOnError
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.visible
import com.squareup.picasso.Picasso
import org.kodein.di.erased.instance
import rx.functions.Action0
import rx.functions.Action1
import java.util.concurrent.TimeUnit

/**
 */
abstract class InboxFragment<T>(name: String) : BaseFragment(name) {
    protected val inboxService: InboxService by instance()
    private val picasso: Picasso by instance()

    private val viewNothingHere: View by bindView(android.R.id.empty)
    private val swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout by bindView(R.id.refresh)

    // views we can reset.
    private var messagesView: androidx.recyclerview.widget.RecyclerView? = null
    private var viewBusyIndicator: View? = null

    private lateinit var loader: LoaderHelper<List<T>>
    private var loadStartedTimestamp = Instant(0)

    init {
        retainInstance = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loader = newLoaderHelper().apply { reload() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_inbox, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBusyIndicator = view.find(R.id.busy_indicator)

        messagesView = view.find<androidx.recyclerview.widget.RecyclerView>(R.id.messages).apply {
            itemAnimator = null
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
        }

        swipeRefreshLayout.setOnRefreshListener { reloadInboxContent() }
        swipeRefreshLayout.setColorSchemeResources(ThemeHelper.accentColor)

        showBusyIndicator()

        // load the messages
        loadStartedTimestamp = Instant.now()
        loader.load(Action1 { onMessagesLoaded(it) }, defaultOnError(), Action0 { hideBusyIndicator() })
    }

    override fun onResume() {
        super.onResume()

        // reload if re-started after one minute
        if (loadStartedTimestamp.plus(1, TimeUnit.MINUTES).isBeforeNow) {
            loadStartedTimestamp = Instant.now()
            reloadInboxContent()
        }
    }

    override fun onDestroyView() {
        loader.detach()
        super.onDestroyView()
    }

    private fun reloadInboxContent() {
        loader.reload()
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
        viewBusyIndicator?.visible = true
    }

    private fun hideBusyIndicator() {
        if (hasView()) {
            viewBusyIndicator?.let { busyIndicator ->
                busyIndicator.visibility = View.GONE
                val parent = busyIndicator.parent
                (parent as ViewGroup).removeView(viewBusyIndicator)

                viewBusyIndicator = null
            }

            swipeRefreshLayout.isRefreshing = false
        }
    }

    protected abstract fun newLoaderHelper(): LoaderHelper<List<T>>

    private fun hasView(): Boolean {
        return messagesView != null
    }

    private fun onMessagesLoaded(messages: List<T>) {
        hideBusyIndicator()
        hideNothingHereIndicator()

        // replace previous adapter with new values
        messagesView?.let { displayMessages(it, messages) }

        if (messages.isEmpty())
            showNothingHereIndicator()
    }

    protected abstract fun displayMessages(recyclerView: androidx.recyclerview.widget.RecyclerView, messages: List<T>)

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
