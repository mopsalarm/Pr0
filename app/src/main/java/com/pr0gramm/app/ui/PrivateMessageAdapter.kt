package com.pr0gramm.app.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.MessageConverter
import com.pr0gramm.app.ui.views.SenderInfoView
import com.pr0gramm.app.ui.views.UsernameView
import com.pr0gramm.app.util.Linkify
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.findOptional
import com.pr0gramm.app.util.visible

/**
 */
class PrivateMessageAdapter(
        private val context: Context, messages: List<Api.PrivateMessage>,
        private val actionListener: MessageActionListener?) : androidx.recyclerview.widget.RecyclerView.Adapter<PrivateMessageAdapter.MessageViewHolder>() {

    private val messages: List<MessageItem> = groupAndSort(messages)

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return messages[position].message.id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.inbox_private_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val item = messages[position]

        // header for each "group"
        val firstOfGroup = position == 0 || messages[position - 1].partner.id != item.partner.id
        holder.header.visible = firstOfGroup
        holder.senderName.setUsername(item.partner.name, item.partner.mark)

        // grey out our messages
        holder.text.setTextColor(ContextCompat.getColor(context,
                if (item.message.isSent) R.color.message_text_sent else R.color.message_text_received))

        // the text of the message
        Linkify.linkifyClean(holder.text, item.message.message)

        // sender info
        holder.sender.setSingleLine(true)
        holder.sender.setSenderName(item.message.senderName, item.message.senderMark)
        holder.sender.hidePointView()
        holder.sender.setDate(item.message.created)

        if (actionListener != null && !item.message.isSent) {
            holder.sender.setOnSenderClickedListener {
                actionListener.onUserClicked(item.message.senderId, item.message.senderName)
            }

            holder.sender.setOnAnswerClickedListener(View.OnClickListener {
                actionListener.onAnswerToPrivateMessage(MessageConverter.of(item.message))
            })
        } else {
            // reset the answer click listener
            holder.sender.setOnAnswerClickedListener(null)
        }
    }

    override fun getItemCount(): Int {
        return messages.size
    }

    class MessageViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val text: TextView = find(R.id.message_text)
        val sender: SenderInfoView = find(R.id.sender_info)
        val senderName: UsernameView = find(R.id.sender_name)
        val header: View = find(R.id.header)
        val divider: View = find(R.id.divider)

        val type: TextView? = findOptional(R.id.message_type)
    }

    private class MessageItem(val message: Api.PrivateMessage, val partner: PartnerKey)

    private class PartnerKey(val id: Int, val name: String, val mark: Int) {
        override fun hashCode(): Int = id

        override fun equals(other: Any?): Boolean {
            return other is PartnerKey && id == other.id
        }
    }

    private fun groupAndSort(messages: List<Api.PrivateMessage>): List<MessageItem> {
        val enhanced = messages.map { message ->
            val outgoing = message.isSent

            val partnerId = if (outgoing) message.recipientId else message.senderId
            val partnerMark = if (outgoing) message.recipientMark else message.senderMark
            val partnerName = if (outgoing) message.recipientName else message.senderName

            val partnerKey = PartnerKey(partnerId, partnerName, partnerMark)
            MessageItem(message, partnerKey)
        }

        return enhanced.groupBy { it.partner }.values.flatMap { it }
    }
}
