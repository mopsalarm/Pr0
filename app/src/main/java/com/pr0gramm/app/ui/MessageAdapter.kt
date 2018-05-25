package com.pr0gramm.app.ui

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.util.inflate


/**
 */
open class MessageAdapter(private val itemLayout: Int,
                          private val actionListener: MessageActionListener,
                          private val currentUsername: String?,
                          messages: List<Api.Message>) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    protected val messages = messages.toMutableList()

    init {
        setHasStableIds(true)
    }

    /**
     * Replace all the messages with the new messages from the given iterable.
     */
    fun setMessages(messages: Iterable<Api.Message>) {
        this.messages.clear()
        this.messages.addAll(messages)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long {
        return messages[position].id()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(itemLayout) as MessageView
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bindTo(message, actionListener, currentUsername)
    }

    override fun getItemCount(): Int {
        return messages.size
    }

    open class MessageViewHolder(private val view: MessageView) : RecyclerView.ViewHolder(view) {
        open fun bindTo(message: Api.Message, actionListener: MessageActionListener?, currentUsername: String? = null) {
            view.update(message, currentUsername)

            if (actionListener != null) {
                view.setOnSenderClickedListener {
                    actionListener.onUserClicked(message.senderId(), message.name())
                }

                val isComment = message.itemId() != 0L
                if (isComment) {
                    view.setAnswerClickedListener {
                        actionListener.onAnswerToCommentClicked(message)
                    }

                    view.setOnClickListener {
                        actionListener.onCommentClicked(message.itemId(), message.id())
                    }

                } else {
                    view.setAnswerClickedListener {
                        actionListener.onAnswerToPrivateMessage(message)
                    }

                    view.setOnClickListener(null)
                }
            }
        }
    }
}
