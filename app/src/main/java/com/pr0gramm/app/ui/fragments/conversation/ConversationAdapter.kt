package com.pr0gramm.app.ui.fragments.conversation

import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.recyclerview.widget.DiffUtil
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.ui.adapters2.BindableViewHolder
import com.pr0gramm.app.ui.adapters2.DelegatingPagingDataAdapter
import com.pr0gramm.app.ui.adapters2.ViewHolders
import com.pr0gramm.app.util.Linkify
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.setTextFuture
import com.pr0gramm.app.util.sp
import java.text.SimpleDateFormat
import java.util.*


sealed class ConversationItem {
    class Message(val message: Api.ConversationMessage) : ConversationItem()
    class Divider(val text: String) : ConversationItem()
}

class ConversationAdapter : DelegatingPagingDataAdapter<ConversationItem>(ItemCallback) {
    override val delegate = ViewHolders<ConversationItem> {
        register(ConversationItemMessageViewHolder.Factory(sentValue = true))
        register(ConversationItemMessageViewHolder.Factory(sentValue = false))
        register(ConversationItemDividerViewHolder.Factory)
    }

    val messages: List<Api.ConversationMessage>
        get() = items.mapNotNull { item -> (item as? ConversationItem.Message)?.message }

    private object ItemCallback : DiffUtil.ItemCallback<ConversationItem>() {
        override fun areItemsTheSame(oldItem: ConversationItem, newItem: ConversationItem): Boolean {
            return when {
                oldItem is ConversationItem.Message && newItem is ConversationItem.Message ->
                    oldItem.message.id == newItem.message.id

                oldItem is ConversationItem.Divider && newItem is ConversationItem.Divider ->
                    oldItem.text == newItem.text

                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ConversationItem, newItem: ConversationItem): Boolean {
            // if the items are the same, the contents is also equal.
            return true
        }
    }
}

private class ConversationItemMessageViewHolder private constructor(parent: ViewGroup, layoutId: Int)
    : BindableViewHolder<ConversationItem.Message>(parent, layoutId) {

    private val format = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val messageView = find<AppCompatTextView>(R.id.message)
    private val timeView = find<TextView>(R.id.time)

    override fun bindTo(value: ConversationItem.Message) {
        val context = itemView.context

        val text = buildSpannedString {
            append(Linkify.linkify(context, value.message.messageText))

            inSpans(SpaceSpan(context.sp(32f).toInt())) {
                append(" ")
            }
        }

        messageView.setTextFuture(text)

        timeView.text = value.message.creationTime.toString(format)
    }

    class Factory(private val sentValue: Boolean) : BindableViewHolder.Factory<ConversationItem.Message> {
        override fun convertValue(value: Any): ConversationItem.Message? {
            return (value as? ConversationItem.Message).takeIf { it?.message?.sent == sentValue }
        }

        override fun createViewHolder(parent: ViewGroup): BindableViewHolder<ConversationItem.Message> {
            val layoutId = if (sentValue) R.layout.item_message_sent else R.layout.item_message_received
            return ConversationItemMessageViewHolder(parent, layoutId)
        }
    }
}

class ConversationItemDividerViewHolder private constructor(parent: ViewGroup)
    : BindableViewHolder<ConversationItem.Divider>(parent, R.layout.item_date_divider) {

    private val textView = find<TextView>(R.id.text)

    override fun bindTo(value: ConversationItem.Divider) {
        textView.text = value.text
    }

    companion object Factory : BindableViewHolder.DefaultFactory<ConversationItem.Divider>(::ConversationItemDividerViewHolder)
}


