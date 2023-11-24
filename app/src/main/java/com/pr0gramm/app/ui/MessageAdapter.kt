package com.pr0gramm.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.api.pr0gramm.MessageType
import com.pr0gramm.app.databinding.ItemDateDividerBinding
import com.pr0gramm.app.ui.fragments.conversation.StringValue
import com.pr0gramm.app.util.inflate
import java.util.Objects

/**
 */
class MessageAdapter(
        private val itemLayout: Int,
        private val actionListener: MessageActionListener,
        private val currentUsername: String?,
        private val paginationController: PaginationController)

    : DelegateAdapter<Any>(DiffCallback()) {

    init {
        delegates += MessageAdapterDelegate()
        delegates += ErrorAdapterDelegate()
        delegates += staticLayoutAdapterDelegate<Loading>(R.layout.feed_hint_loading)
        delegates += staticLayoutAdapterDelegate(R.layout.feed_hint_empty, EmptyValue)

        delegates += run {
            val itemAdapter = Adapters.ForViewBindings(ItemDateDividerBinding::inflate) { (views), item: StringValue ->
                views.text.text = item.text
            }

            Adapters.adapt(itemAdapter) { value -> value as? StringValue }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        paginationController.hit(position, items.size)
        super.onBindViewHolder(holder, position)
    }

    class DiffCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            if (oldItem is Message && newItem is Message) {
                return oldItem.id == newItem.id
            }

            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return Objects.equals(oldItem, newItem)
        }
    }

    inner class MessageAdapterDelegate : ListItemTypeAdapterDelegate<Message, Any, MessageViewHolder>(Message::class) {
        override fun onCreateViewHolder(parent: ViewGroup): MessageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(itemLayout) as MessageView
            return MessageViewHolder(view)
        }

        override fun onBindViewHolder(holder: MessageViewHolder, value: Message) {
            holder.bindTo(value, actionListener, currentUsername)
        }
    }

    object EmptyValue

    open class MessageViewHolder(private val view: MessageView) : RecyclerView.ViewHolder(view) {
        open fun bindTo(message: Message, actionListener: MessageActionListener?, currentUsername: String? = null) {
            view.update(message, currentUsername)

            if (actionListener != null) {
                view.setOnSenderClickedListener {
                    actionListener.onUserClicked(message.senderId, message.name)
                }

                when (message.type) {
                    MessageType.COMMENT -> {
                        view.setAnswerClickedListener(R.string.action_answer) {
                            actionListener.onAnswerToCommentClicked(message)
                        }

                        view.setOnClickListener {
                            actionListener.onCommentClicked(message)
                        }
                    }

                    MessageType.MESSAGE -> {
                        view.setAnswerClickedListener(R.string.action_to_conversation) {
                            actionListener.onAnswerToPrivateMessage(message)
                        }

                        view.setOnClickListener(null)
                    }

                    MessageType.STALK -> {
                        view.clearAnswerClickedListener()

                        view.setOnClickListener {
                            actionListener.onCommentClicked(message)
                        }
                    }

                    else -> {
                        view.clearAnswerClickedListener()
                        view.setOnClickListener(null)
                    }
                }
            }
        }
    }
}

class Loading
