package com.pr0gramm.app.ui.fragments

import android.content.Context
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
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
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
import com.pr0gramm.app.ui.base.withViewDisabled
import com.pr0gramm.app.util.*
import org.kodein.di.erased.instance
import java.text.SimpleDateFormat
import java.util.*

/**
 */
class ConversationFragment : BaseFragment("ConversationFragment") {
    private val inboxService: InboxService by instance()

    private val refreshLayout: SwipeRefreshLayout by bindView(R.id.refresh)
    private val listView: RecyclerView by bindView(R.id.messages)

    private val messageText: TextView by bindView(R.id.message_input)
    private val buttonSend: ImageButton by bindView(R.id.action_send)

    // the pagination thats backing the list view
    private lateinit var pagination: Pagination<Api.ConversationMessage>

    var conversationName: String by fragmentArgument()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_conversation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(listView) {
            itemAnimator = null
            layoutManager = LinearLayoutManager(activity).apply { stackFromEnd = true }
            addItemDecoration(SpacingItemDecoration(dp = 16))
        }

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

        reloadConversation()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_conversation, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return false != when (item.itemId) {
            R.id.action_refresh -> reloadConversation()
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
        val message = messageText.text.toString()

        launchWithErrorHandler {
            withViewDisabled(messageText, buttonSend) {
                // do the real posting
                val messages = inboxService.send(conversationName, message)

                // convert to pagination state
                val state = Pagination.State(values = messages.messages,
                        tailState = Pagination.EndState(hasMore = messages.atEnd))

                // clear the input field
                messageText.text = ""

                // remove backup value
                BACKUP.remove(conversationName)

                // and reset the conversationtoll!
                resetConversation(state)
            }
        }
    }

    private fun reloadConversation() {
        resetConversation(Pagination.State.hasMoreState())
    }

    private fun resetConversation(initialState: Pagination.State<Api.ConversationMessage>) {
        refreshLayout.isRefreshing = false

        val pagination = Pagination(this, ConversationLoader(conversationName, inboxService), initialState)
        listView.adapter = ConversationAdapter(requireContext(), pagination)

        this.pagination = pagination
    }

    companion object {
        private val BACKUP = mutableMapOf<String, String>()
    }
}

private class ConversationAdapter(private val context: Context, pagination: Pagination<Api.ConversationMessage>)
    : PaginationRecyclerViewAdapter<Api.ConversationMessage, Any>(pagination, ConversationItemDiffCallback()) {

    init {
        delegates += MessageAdapterDelegate(sentValue = true)
        delegates += MessageAdapterDelegate(sentValue = false)
        delegates += DividerAdapterDelegate()
        delegates += ErrorAdapterDelegate()
        delegates += staticLayoutAdapterDelegate<Loading>(R.layout.feed_hint_loading)
        delegates += staticLayoutAdapterDelegate(R.layout.item_conversation_empty, NoConversationsValue)
    }

    override fun translateState(state: Pagination.State<Api.ConversationMessage>): List<Any> {
        val values = mutableListOf<Any>()

        val f = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val dates = state.values.map { message -> message.creationTime.toString(f) }

        state.values.forEachIndexed { index, message ->
            values += message

            if (index > 0 && dates[index] != dates.getOrNull(index + 1)) {
                values += DividerAdapterDelegate.Value(dates[index])
            }
        }

        addEndStateToValues(context, values, state.tailState,
                ifEmptyValue = NoConversationsValue)

        return values.reversed()
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

    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        return oldItem == newItem
    }
}

private class ConversationLoader(private val name: String, private val inboxService: InboxService) : Pagination.Loader<Api.ConversationMessage>() {
    override suspend fun loadAfter(currentValues: List<Api.ConversationMessage>): StateTransform<Api.ConversationMessage> {
        val olderThan = currentValues.lastOrNull()?.creationTime

        val response = inboxService.messagesInConversation(name, olderThan)
        if (response.error != null) {
            throw StringException { context -> context.getString(R.string.conversation_load_error) }
        }

        return { state ->
            state.copy(
                    values = state.values + response.messages,
                    tailState = state.tailState.copy(hasMore = !response.atEnd))
        }
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

        holder.message.movementMethod = NonCrashingLinkMovementMethod

        holder.message.text = buildSpannedString {
            append(Linkify.linkify(context, value.message))

            inSpans(SpaceSpan(context.dip2px(32f).toInt())) {
                append(" ")
            }
        }

        holder.time.text = value.creationTime.toString(format)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val message = find<TextView>(R.id.message)
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
