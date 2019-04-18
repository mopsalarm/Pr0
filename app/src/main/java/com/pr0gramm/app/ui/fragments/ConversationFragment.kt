package com.pr0gramm.app.ui.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.text.TextPaint
import android.text.style.ReplacementSpan
import android.view.*
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.pr0gramm.app.Duration.Companion.seconds
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.parcel.*
import com.pr0gramm.app.services.InboxService
import com.pr0gramm.app.services.NotificationService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.*
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindView
import com.pr0gramm.app.ui.base.launchIgnoreErrors
import com.pr0gramm.app.ui.base.withViewDisabled
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.instance
import java.text.SimpleDateFormat
import java.util.*

/**
 */
class ConversationFragment : BaseFragment("ConversationFragment") {
    private val inboxService: InboxService by instance()
    private val notifyService: NotificationService by instance()

    private val refreshLayout: SwipeRefreshLayout by bindView(R.id.refresh)
    private val listView: RecyclerView by bindView(R.id.messages)

    private val messageText: TextView by bindView(R.id.message_input)
    private val buttonSend: ImageButton by bindView(R.id.action_send)

    // the pagination that is backing the list view
    private lateinit var pagination: Pagination<Api.ConversationMessage>
    private lateinit var adapter: ConversationAdapter

    // the messages that are backing the adapter
    private var state by observeChange(State()) { updateAdapterState() }

    var conversationName: String by fragmentArgument()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pagination = Pagination(this, ConversationLoader(conversationName, inboxService))
        adapter = ConversationAdapter(PaginationController(pagination, tailOffset = 32))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_conversation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listView.itemAnimator = null
        listView.layoutManager = LinearLayoutManager(activity).apply { stackFromEnd = true }
        listView.adapter = adapter
        listView.addItemDecoration(SpacingItemDecoration(dp = 16))

        setTitle(conversationName)
        setHasOptionsMenu(true)

        refreshLayout.setOnRefreshListener { reloadConversation() }
        refreshLayout.setColorSchemeResources(ThemeHelper.accentColor)

        // restore backup if available
        messageText.text = BACKUP[conversationName] ?: ""

        messageText.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                buttonSend.isEnabled = s.toString().isNotBlank()

                // backup to restore it later.
                BACKUP[conversationName] = s.toString()
            }
        })

        buttonSend.setOnClickListener {
            sendInboxMessage()
        }

        val previousState = savedInstanceState?.getFreezable("ConversationFragment.state", State)
        if (previousState != null) {
            state = previousState
            pagination.initialize(tailState = previousState.tailState)

        } else {
            reloadConversation()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putFreezable("ConversationFragment.state", state)
    }

    override suspend fun onResumeImpl() {
        pagination.updates.bindToLifecycle().subscribe { (state, newValues) ->
            applyPaginationUpdate(state, newValues)
        }

        launchIgnoreErrors {
            runEvery(initial = seconds(15), period = seconds(15)) {
                val shouldUpdate = isAtConversationTail()
                if (shouldUpdate) {
                    refreshConversation()
                }
            }
        }

        // remove notification
        notifyService.cancelForUnreadConversation(conversationName)
    }

    /**
     * Returns true, if the user has scrolled to the current end of the conversation.
     */
    private fun isAtConversationTail(): Boolean {
        val llm = listView.layoutManager as? LinearLayoutManager
        return llm?.findLastCompletelyVisibleItemPosition() == adapter.itemCount - 1
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_conversation, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return false != when (item.itemId) {
            R.id.action_refresh -> {
                scrollToEndOfConversation(force = true)

                launchWithErrorHandler {
                    refreshConversation()
                }
            }

            R.id.action_profile -> openUserProfile()

            else -> super.onOptionsItemSelected(item)
        }
    }

    private suspend fun refreshConversation() {
        mergeWithMessages(inboxService.messagesInConversation(conversationName))
    }

    private fun applyPaginationUpdate(paginationState: Pagination.State<Api.ConversationMessage>, newValues: List<Api.ConversationMessage>) {
        state = state.copy(
                messages = state.messages + newValues,
                tailState = paginationState.tailState)
    }

    private fun updateAdapterState() {
        val f = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val dates = state.messages.map { it.creationTime.toString(f) }

        // We will be adding messages from bottom to top (.asReserved())
        // ...
        // We need to add a timestamp containing the date of the current message
        // after adding the message itself, if the next messages date will differ.
        //
        val values = mutableListOf<Any>()
        state.messages.forEachIndexed { index, message ->
            values += message

            if (dates[index] != dates.getOrNull(index + 1)) {
                values += DividerAdapterDelegate.Value(dates[index])
            }
        }

        addEndStateToValues(requireContext(), values, state.tailState, ifEmptyValue = NoConversationsValue)

        adapter.submitList(values.asReversed())
    }

    private fun openUserProfile() {
        requireContext().startActivity<MainActivity> { intent ->
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.parse("https://pr0gramm.com/user/$conversationName")
        }
    }

    private fun sendInboxMessage() {
        val message = messageText.text.toString()
        scrollToEndOfConversation(force = true)

        launchWithErrorHandler {
            withViewDisabled(messageText, buttonSend) {
                // do the real posting
                val response = inboxService.send(conversationName, message)

                mergeWithMessages(response)

                // clear the input field
                messageText.text = ""

                // remove backup value
                BACKUP.remove(conversationName)
            }
        }
    }

    private fun mergeWithMessages(response: Api.ConversationMessages) {
        scrollToEndOfConversation(force = false)

        // merge messages into the state
        state = State(
                messages = response.messages, tailState = Pagination.EndState(
                hasMore = !response.atEnd, value = response.messages.lastOrNull()))

        pagination.initialize(tailState = state.tailState)
    }

    private fun scrollToEndOfConversation(force: Boolean = false) {
        if (force) {
            val index = adapter.itemCount - 1
            if (index >= 0) {
                listView.smoothScrollToPosition(index)
            }

        } else {
            adapter.updates.take(1).subscribe {
                scrollToEndOfConversation(force = true)
            }
        }
    }

    private fun reloadConversation() {
        refreshLayout.isRefreshing = false

        state = State()
        pagination.initialize()
    }

    data class State(
            val messages: List<Api.ConversationMessage> = listOf(),
            val tailState: Pagination.EndState<Api.ConversationMessage> = Pagination.EndState(hasMore = true)) : Freezable {

        override fun freeze(sink: Freezable.Sink) {
            sink.writeValues(messages.map { ConversationMessageFreezer(it) })
            sink.writeBoolean(tailState.hasMore)
        }

        companion object : Unfreezable<State> {
            @JvmField
            val CREATOR = parcelableCreator()

            override fun unfreeze(source: Freezable.Source): State {
                val messages = source.readValues(ConversationMessageFreezer).map { it.message }
                val hasMore = source.readBoolean()
                return State(messages, Pagination.EndState(
                        value = messages.lastOrNull(), hasMore = hasMore))
            }
        }
    }

    companion object {
        private val BACKUP = mutableMapOf<String, String>()
    }
}

private class ConversationAdapter(private val paginationController: PaginationController)
    : DelegateAdapter<Any>(ConversationItemDiffCallback()) {

    init {
        delegates += MessageAdapterDelegate(sentValue = true)
        delegates += MessageAdapterDelegate(sentValue = false)
        delegates += DividerAdapterDelegate()
        delegates += ErrorAdapterDelegate()
        delegates += staticLayoutAdapterDelegate<Loading>(R.layout.feed_hint_loading)
        delegates += staticLayoutAdapterDelegate(R.layout.item_conversation_empty, NoConversationsValue)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        paginationController.hit(position, items.size)
        super.onBindViewHolder(holder, position)
    }
}

private object NoConversationsValue

private class ConversationItemDiffCallback : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        return when {
            oldItem is Api.ConversationMessage && newItem is Api.ConversationMessage ->
                oldItem.id == newItem.id

            oldItem is DividerAdapterDelegate.Value && newItem is DividerAdapterDelegate.Value ->
                oldItem.text == newItem.text

            else ->
                newItem == oldItem
        }
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        return oldItem == newItem
    }
}

private class ConversationLoader(private val name: String, private val inboxService: InboxService) : Pagination.Loader<Api.ConversationMessage>() {
    override suspend fun loadAfter(currentValue: Api.ConversationMessage?): Pagination.Page<Api.ConversationMessage> {
        val olderThan = currentValue?.creationTime

        val response = inboxService.messagesInConversation(name, olderThan)
        if (response.error != null) {
            throw StringException("load") { context -> context.getString(R.string.conversation_load_error) }
        }

        return Pagination.Page.atTail(response.messages, hasMore = !response.atEnd)
    }
}

private class MessageAdapterDelegate(private val sentValue: Boolean)
    : ItemAdapterDelegate<Api.ConversationMessage, Any, MessageAdapterDelegate.ViewHolder>() {

    private val format = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun isForViewType(value: Any): Boolean {
        return value is Api.ConversationMessage && value.sent == sentValue
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val layout = if (sentValue) R.layout.item_message_sent else R.layout.item_message_received
        return ViewHolder(parent.inflateDetachedChild(layout))
    }

    override fun onBindViewHolder(holder: ViewHolder, value: Api.ConversationMessage) {
        val context = holder.message.context

        val text = buildSpannedString {
            append(Linkify.linkify(context, value.message))

            inSpans(SpaceSpan(context.dip2px(32f).toInt())) {
                append(" ")
            }
        }

        holder.message.movementMethod = NonCrashingLinkMovementMethod
        holder.message.setTextFuture(text)

        holder.time.text = value.creationTime.toString(format)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val message = find<AppCompatTextView>(R.id.message)
        val time = find<TextView>(R.id.time)
    }
}

class DividerAdapterDelegate
    : ListItemTypeAdapterDelegate<DividerAdapterDelegate.Value, DividerAdapterDelegate.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(parent.inflateDetachedChild(R.layout.item_date_divider))
    }

    override fun onBindViewHolder(holder: ViewHolder, value: Value) {
        holder.textView.text = value.text
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView = find<TextView>(R.id.text)
    }

    data class Value(val text: String)
}

private class SpaceSpan(private val width: Int) : ReplacementSpan(), Parcelable {
    constructor(parcel: Parcel) : this(parcel.readInt())

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        return width
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {}

    override fun updateMeasureState(textPaint: TextPaint) {}

    override fun updateDrawState(tp: TextPaint?) {}

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(width)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<SpaceSpan> {
        override fun createFromParcel(parcel: Parcel): SpaceSpan {
            return SpaceSpan(parcel)
        }

        override fun newArray(size: Int): Array<SpaceSpan?> {
            return arrayOfNulls(size)
        }
    }
}

private class ConversationMessageFreezer(val message: Api.ConversationMessage) : Freezable {
    override fun freeze(sink: Freezable.Sink) = with(sink) {
        writeLong(message.id)
        write(message.creationTime)
        writeString(message.message)
        writeBoolean(message.sent)
    }

    companion object : Unfreezable<ConversationMessageFreezer> {
        @JvmField
        val CREATOR = parcelableCreator()

        override fun unfreeze(source: Freezable.Source): ConversationMessageFreezer {
            return ConversationMessageFreezer(Api.ConversationMessage(
                    id = source.readLong(),
                    creationTime = source.read(Instant),
                    message = source.readString(),
                    sent = source.readBoolean()))
        }
    }
}
