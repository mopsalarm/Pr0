package com.pr0gramm.app.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.util.inflate

/**
 */
class MessageAdapter(
        private val context: Context,
        private val itemLayout: Int,
        private val actionListener: MessageActionListener,
        private val currentUsername: String?,
        pagination: Pagination<Api.Message>)

    : PaginationRecyclerViewAdapter<Api.Message, Any>(pagination, DiffCallback()) {

    init {
        delegates += MessageAdapterDelegate()
        delegates += staticLayoutAdapterDelegate<Loading>(R.layout.feed_hint_loading)
        delegates += ErrorAdapterDelegate()
        delegates += staticLayoutAdapterDelegate(R.layout.feed_hint_empty, EmptyValue)
    }

    override fun translateState(state: Pagination.State<Api.Message>): List<Any> {
        val values = state.values.toMutableList<Any>()
        if (state.tailState.hasMore) {
            values += Loading()
        }

        if (state.tailState.error != null) {
            values += ErrorAdapterDelegate.errorValueOf(context, state.tailState.error)
        }

        if (values.isEmpty()) {
            values += EmptyValue
        }

        return values
    }

    class DiffCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            if (oldItem is Api.Message && newItem is Api.Message) {
                return oldItem.id == newItem.id
            }

            return oldItem === newItem
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return oldItem == newItem
        }
    }

    inner class MessageAdapterDelegate : ListItemTypeAdapterDelegate<Api.Message, MessageAdapter.MessageViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup): MessageAdapter.MessageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(itemLayout) as MessageView
            return MessageViewHolder(view)
        }

        override fun onBindViewHolder(holder: MessageViewHolder, value: Api.Message) {
            holder.bindTo(value, actionListener, currentUsername)
        }
    }

    private object EmptyValue

    open class MessageViewHolder(private val view: MessageView) : RecyclerView.ViewHolder(view) {
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

class Loading
