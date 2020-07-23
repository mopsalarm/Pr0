package com.pr0gramm.app.ui.fragments.conversation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.ImageButton
import android.widget.TextView
import androidx.paging.*
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.NotificationService
import com.pr0gramm.app.ui.MainActivity
import com.pr0gramm.app.ui.SimpleTextWatcher
import com.pr0gramm.app.ui.base.*
import com.pr0gramm.app.ui.fragments.EndOfViewSmoothScroller
import com.pr0gramm.app.ui.viewModels
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.instance
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.seconds

/**
 */
class ConversationFragment : BaseFragment("ConversationFragment") {
    private val notifyService: NotificationService by instance()

    private val listView: RecyclerView by bindView(R.id.messages)

    private val messageText: TextView by bindView(R.id.message_input)
    private val buttonSend: ImageButton by bindView(R.id.action_send)

    private val conversationAdapter = ConversationAdapter()
    private val pendingMessagesAdapter = PendingMessagesAdapter()
    private val adapter = ConcatAdapter()

    var conversationName: String by fragmentArgument()

    private val model: ConversationViewModel by viewModels {
        ConversationViewModel(instance(), conversationName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val header = SimpleLoadStateAdapter()
        val footer = SimpleLoadStateAdapter()

        conversationAdapter.addLoadStateListener { loadStates ->
            header.loadState = loadStates.prepend

            if (loadStates.refresh !is LoadState.Loading || conversationAdapter.itemCount == 0) {
                footer.loadState = loadStates.refresh
            }
        }

        adapter.addAdapter(header)
        adapter.addAdapter(conversationAdapter)
        adapter.addAdapter(pendingMessagesAdapter)
        adapter.addAdapter(footer)

        launchWhenResumed {
            while (true) {
                delay(15.seconds)

                if (isAtConversationTail()) {
                    conversationAdapter.refresh()
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_conversation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        launchInViewScope {
            model.paging.collectLatest { pg -> updateConversation(pg) }
        }

        launchInViewScope {
            model.pendingMessages.collect { pendingMessages ->
                pendingMessagesAdapter.submitList(pendingMessages)
            }
        }

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            private var maxMessageId: Long = 0

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                // scroll to the end if we get a new message
                val maxMessage = conversationAdapter.messages.lastOrNull()
                if (maxMessage != null && maxMessage.id > maxMessageId) {
                    maxMessageId = maxMessage.id
                    return scrollToEndOfConversation()
                }

                // scroll to the end if we've added a message into the set of pending messages
                if (positionStart == adapter.lastIndex && pendingMessagesAdapter.itemCount > 0) {
                    return scrollToEndOfConversation()
                }
            }
        })

        listView.itemAnimator = null

        listView.layoutManager = LinearLayoutManager(activity).apply {
            stackFromEnd = true
        }

        listView.adapter = adapter

        setTitle(conversationName)
        setHasOptionsMenu(true)

        messageText.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                buttonSend.isEnabled = s.toString().isNotBlank()
            }
        })

        TextViewCache.addCaching(messageText, "conversation:$conversationName")

        buttonSend.setOnClickListener {
            sendInboxMessage()
        }
    }

    private suspend fun updateConversation(pg: PagingData<Api.ConversationMessage>) {
        val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

        val messages = pg.map { ConversationItem.Message(it) }

        val divided = messages.insertSeparators<ConversationItem.Message, ConversationItem> { prev, next ->
            val prevStr = prev?.message?.creationTime?.toString(fmt)
            val nextStr = next?.message?.creationTime?.toString(fmt)
            if (nextStr != null && prevStr != nextStr) ConversationItem.Divider(nextStr) else null
        }

        conversationAdapter.submitData(divided)
    }

    override fun onResume() {
        super.onResume()

        // remove notification
        notifyService.cancelForUnreadConversation(conversationName)
    }

    /**
     * Returns true, if the user has scrolled to the current end of the conversation.
     */
    private fun isAtConversationTail(): Boolean {
        val llm = listView.layoutManager as? LinearLayoutManager
        return llm?.findLastVisibleItemPosition() == adapter.lastIndex
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_conversation, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return false != when (item.itemId) {
            R.id.action_refresh -> {
                launchUntilViewDestroy {
                    scrollToEndOfConversation()
                    conversationAdapter.refresh()
                }
            }

            R.id.action_profile -> openUserProfile()

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openUserProfile() {
        requireContext().startActivity<MainActivity> { intent ->
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.parse("https://pr0gramm.com/user/$conversationName")
        }
    }

    private fun sendInboxMessage() {
        val message = messageText.text.trim().toString()

        // directly clear the input field
        messageText.text = ""

        // send the message
        launchWhenCreated { model.send(message) }
    }

    private fun scrollToEndOfConversation() {
        if (adapter.lastIndex >= 0) {
            val lm = listView.layoutManager as LinearLayoutManager
            lm.startSmoothScroll(EndOfViewSmoothScroller(requireContext(), adapter.lastIndex))
        }
    }
}


val RecyclerView.Adapter<*>.lastIndex: Int
    get() = itemCount - 1

class SimpleLoadStateAdapter : LoadStateAdapter<RecyclerView.ViewHolder>() {
    override fun getStateViewType(loadState: LoadState): Int {
        return when (loadState) {
            is LoadState.Loading -> R.layout.feed_hint_loading
            is LoadState.Error -> R.layout.feed_error
            else -> throw UnsupportedOperationException("Unsupported load state: $loadState")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): RecyclerView.ViewHolder {
        val resId = getStateViewType(loadState)
        return object : RecyclerView.ViewHolder(parent.inflateDetachedChild<View>(resId)) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, loadState: LoadState) {
        if (loadState is LoadState.Error) {
            val errorView: TextView = holder.itemView.find(R.id.error)
            errorView.text = ErrorFormatting.format(holder.itemView.context, loadState.error)
        }
    }
}
