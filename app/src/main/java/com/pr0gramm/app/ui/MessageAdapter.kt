package com.pr0gramm.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.util.inflate


/**
 */
class MessageAdapter(private val itemLayout: Int,
                     private val actionListener: MessageActionListener,
                     private val currentUsername: String?,
                     messages: List<Api.Message>) : DelegatedAsyncListAdapter<Api.Message>() {

    init {
        delegates += MessageAdapterDelegate()

        submitList(messages)
    }


    /**
     * Replace all the messages with the new messages from the given iterable.
     */
    fun setMessages(messages: Iterable<Api.Message>) {
        this.submitList(messages.toList())
    }

    inner class MessageAdapterDelegate : ItemAdapterDelegate<Api.Message, Api.Message, MessageViewHolder>() {
        override fun isForViewType(value: Api.Message): Boolean {
            return true
        }

        override fun onCreateViewHolder(parent: ViewGroup): MessageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(itemLayout) as MessageView
            return MessageViewHolder(view)
        }

        override fun onBindViewHolder(holder: MessageViewHolder, value: Api.Message) {
            holder.bindTo(value, actionListener, currentUsername)
        }
    }

    open class MessageViewHolder(private val view: MessageView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        open fun bindTo(message: Api.Message, actionListener: MessageActionListener?, currentUsername: String? = null) {
            view.update(message, currentUsername)

            if (actionListener != null) {
                view.setOnSenderClickedListener {
                    actionListener.onUserClicked(message.senderId, message.name)
                }

                val isComment = message.itemId != 0L
                if (isComment) {
                    view.setAnswerClickedListener {
                        actionListener.onAnswerToCommentClicked(message)
                    }

                    view.setOnClickListener {
                        actionListener.onCommentClicked(message)
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
