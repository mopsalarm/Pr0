package com.pr0gramm.app.ui.fragments

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.text.TextPaint
import android.text.style.ReplacementSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.pr0gramm.app.util.dip2px
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.fragmentArgument
import com.pr0gramm.app.util.inflateDetachedChild
import org.kodein.di.erased.instance
import java.text.SimpleDateFormat
import java.util.*

/**
 */
class ConversationFragment : BaseFragment("ConversationFragment") {
    private val inboxService: InboxService by instance()

    private val refreshLayout: SwipeRefreshLayout by bindView(R.id.refresh)
    private val listView: RecyclerView by bindView(R.id.messages)

    var conversationName: String by fragmentArgument()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_conversation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(listView) {
            itemAnimator = null
            layoutManager = LinearLayoutManager(activity)
            addItemDecoration(SpacingItemDecoration(dp = 8))
        }

        refreshLayout.setOnRefreshListener { reloadConversation() }
        refreshLayout.setColorSchemeResources(ThemeHelper.accentColor)

        setTitle(conversationName)

        reloadConversation()
    }

    private fun reloadConversation() {
        refreshLayout.isRefreshing = false
        listView.adapter = ConversationAdapter()
    }

    private fun makeConversationsPagination(): Pagination<Api.ConversationMessage> {
        return Pagination(this, ConversationLoader(conversationName, inboxService), Pagination.State.hasMoreState())
    }

    inner class ConversationAdapter : PaginationRecyclerViewAdapter<Api.ConversationMessage, Any>(
            makeConversationsPagination(),
            ConversationItemDiffCallback()) {

        init {
            delegates += MessageAdapterDelegate(sentValue = true)
            delegates += MessageAdapterDelegate(sentValue = false)
            delegates += staticLayoutAdapterDelegate<Loading>(R.layout.feed_hint_loading)
            delegates += ErrorAdapterDelegate()
            delegates += DateDividerAdapterDelegate()
        }

        override fun translateState(state: Pagination.State<Api.ConversationMessage>): List<Any> {
            val values = mutableListOf<Any>()

            val f = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val dates = state.values.map { message -> message.creationTime.toString(f) }

            state.values.forEachIndexed { index, message ->
                if (index > 0 && dates[index - 1] != dates[index]) {
                    values += DateDividerValue(dates[index])
                }

                values += message
            }

            if (state.tailState.error != null) {
                values += LoadError(state.tailState.error.toString())
            }

            if (state.tailState.loading) {
                values += Loading()
            }

            return values
        }
    }
}

private data class DateDividerValue(val date: String)

private class ConversationItemDiffCallback : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        return when {
            oldItem is Api.ConversationMessage && newItem is Api.ConversationMessage ->
                oldItem.id == newItem.id

            oldItem is DateDividerValue && newItem is DateDividerValue ->
                oldItem.date == newItem.date

            else ->
                newItem === oldItem
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

        holder.message.text = buildSpannedString {
            append(value.message)
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

private class DateDividerAdapterDelegate
    : ListItemTypeAdapterDelegate<DateDividerValue, DateDividerAdapterDelegate.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(parent.inflateDetachedChild(R.layout.item_date_divider))
    }

    override fun onBindViewHolder(holder: ViewHolder, value: DateDividerValue) {
        holder.date.text = value.date
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val date = find<TextView>(R.id.date)
    }
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