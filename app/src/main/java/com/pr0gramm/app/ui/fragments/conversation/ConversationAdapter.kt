package com.pr0gramm.app.ui.fragments.conversation

import android.content.Context
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.databinding.ItemDateDividerBinding
import com.pr0gramm.app.databinding.ItemMessageReceivedBinding
import com.pr0gramm.app.databinding.ItemMessageSentBinding
import com.pr0gramm.app.ui.AdapterDelegate
import com.pr0gramm.app.ui.AdapterDelegateManager
import com.pr0gramm.app.ui.Adapters
import com.pr0gramm.app.ui.adapters2.DelegatingPagingDataAdapter
import com.pr0gramm.app.util.Linkify
import com.pr0gramm.app.util.setTextFuture
import com.pr0gramm.app.util.sp
import java.text.SimpleDateFormat
import java.util.Locale


sealed class ConversationItem {
    class Message(val message: Api.ConversationMessage) : ConversationItem()
    class Divider(val text: String) : ConversationItem()
}

class ConversationAdapter : DelegatingPagingDataAdapter<ConversationItem>(ItemCallback) {

    override val manager = AdapterDelegateManager(listOf(
            conversationItemMessageViewHolder(true),
            conversationItemMessageViewHolder(false),
            conversationItemDividerAdapter(),
    ))

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

fun conversationItemMessageViewHolder(forSent: Boolean): AdapterDelegate<ConversationItem, RecyclerView.ViewHolder> {
    val format = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun bindTo(timeView: TextView, messageView: AppCompatTextView, value: ConversationItem.Message) {
        val context: Context = timeView.context

        val text = buildSpannedString {
            append(Linkify.linkify(context, value.message.messageText))

            inSpans(SpaceSpan(context.sp(32f).toInt())) {
                append(" ")
            }
        }

        messageView.setTextFuture(text)

        timeView.text = value.message.creationTime.toString(format)
    }

    return if (forSent) {
        val itemAdapter = Adapters.ForViewBindings(ItemMessageSentBinding::inflate) { (views), item: ConversationItem.Message ->
            bindTo(views.time, views.message, item)
        }

        Adapters.adapt(itemAdapter) { value: ConversationItem ->
            (value as? ConversationItem.Message)?.takeIf { msg -> msg.message.sent }
        }
    } else {
        val itemAdapter = Adapters.ForViewBindings(ItemMessageReceivedBinding::inflate) { (views), item: ConversationItem.Message ->
            bindTo(views.time, views.message, item)
        }

        Adapters.adapt(itemAdapter) { value: ConversationItem ->
            (value as? ConversationItem.Message)?.takeIf { msg -> !msg.message.sent }
        }
    }
}

fun conversationItemDividerAdapter(): AdapterDelegate<ConversationItem, RecyclerView.ViewHolder> {
    val itemAdapter = Adapters.ForViewBindings(ItemDateDividerBinding::inflate) { (views), item: ConversationItem.Divider ->
        views.text.text = item.text
    }

    return Adapters.adapt(itemAdapter) { value: ConversationItem -> value as? ConversationItem.Divider }
}
