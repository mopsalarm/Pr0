package com.pr0gramm.app.ui.fragments.conversation

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.util.Linkify
import com.pr0gramm.app.util.dp
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.inflateDetachedChild
import com.pr0gramm.app.util.setTextFuture

class PendingMessagesAdapter : ListAdapter<String, PendingMessagesAdapter.ViewHolder>(ItemCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent.inflateDetachedChild<View>(R.layout.item_message_sent))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.set(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageView = itemView.find<TextView>(R.id.message)
        private val timeView = itemView.find<TextView>(R.id.time)

        init {
            timeView.text = itemView.context.getString(R.string.hint_sending)
            itemView.alpha = 0.5f
        }

        fun set(messageText: String) {
            val context: Context = itemView.context

            val text = buildSpannedString {
                append(Linkify.linkify(context, messageText))

                inSpans(SpaceSpan(context.dp(32f).toInt())) {
                    append(" ")
                }
            }

            messageView.setTextFuture(text)
        }
    }

    private object ItemCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem === newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}
