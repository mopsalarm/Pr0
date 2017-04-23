package com.pr0gramm.app.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.salomonbrys.kodein.instance
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
import org.joda.time.Duration.standardMinutes
import org.joda.time.Instant
import rx.functions.Action0
import rx.functions.Action1

/**
 */
abstract class InboxFragment<T>(name: String) : BaseFragment(name) {
    protected val inboxService: InboxService by instance()
    private val picasso: Picasso by instance()

    private val viewNothingHere: View by bindView(android.R.id.empty)
    private val swipeRefreshLayout: SwipeRefreshLayout by bindView(R.id.refresh)

    // views we can reset.
    private var messagesView: RecyclerView? = null
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

        messagesView = view.find<RecyclerView>(R.id.messages).apply {
            itemAnimator = null
            layoutManager = LinearLayoutManager(activity)
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
        if (loadStartedTimestamp.plus(standardMinutes(1)).isBeforeNow) {
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
            startActivity(WriteMessageActivity.intent(activity, message))
        }

        override fun onAnswerToCommentClicked(comment: Api.Message) {
            startActivity(WriteMessageActivity.answerToComment(activity, comment))
        }

        override fun onNewPrivateMessage(userId: Long, name: String) {
            startActivity(WriteMessageActivity.intent(activity, userId, name))
        }

        override fun onCommentClicked(itemId: Long, commentId: Long) {
            open(UriHelper.of(activity).post(FeedType.NEW, itemId, commentId))
        }

        private fun open(uri: Uri) {
            val intent = Intent(Intent.ACTION_VIEW, uri, activity, MainActivity::class.java)
            startActivity(intent)
        }

        override fun onUserClicked(userId: Int, username: String) {
            open(UriHelper.of(activity).uploads(username))
        }
    }

    companion object {
        const val ARG_INBOX_TYPE = "InboxFragment.inboxType"
    }
}
