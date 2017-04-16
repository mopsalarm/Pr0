package com.pr0gramm.app.ui

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.UserService


/**
 */
open class MessageAdapter(private val itemLayout: Int, context: Context,
                          messages: List<Api.Message>) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val userName: String? = context.appKodein().instance<UserService>().name.orNull()
    protected val messages = messages.toMutableList()

    var pointsVisibility: MessageView.PointsVisibility = MessageView.PointsVisibility.CONDITIONAL
    var actionListener: MessageActionListener? = null

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
        val view = LayoutInflater.from(parent.context).inflate(itemLayout, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val view = holder.view

        view.update(message, userName, pointsVisibility)

        if (actionListener != null) {
            view.setOnSenderClickedListener {
                actionListener?.onUserClicked(message.senderId(), message.name())
            }

            val isComment = message.itemId() != 0L
            if (isComment) {
                view.setAnswerClickedListener(View.OnClickListener {
                    actionListener?.onAnswerToCommentClicked(message)
                })

                view.setOnClickListener {
                    actionListener?.onCommentClicked(message.itemId(), message.id())
                }

            } else {
                view.setAnswerClickedListener(View.OnClickListener {
                    actionListener?.onAnswerToPrivateMessage(message)
                })

                view.setOnClickListener(null)
            }
        }
    }

    override fun getItemCount(): Int {
        return messages.size
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val view: MessageView = itemView as MessageView
    }
}
