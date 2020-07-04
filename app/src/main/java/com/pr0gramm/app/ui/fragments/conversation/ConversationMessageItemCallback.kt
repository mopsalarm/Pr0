package com.pr0gramm.app.ui.fragments.conversation

import androidx.recyclerview.widget.DiffUtil
import com.pr0gramm.app.api.pr0gramm.Api

private object ConversationMessageItemCallback : DiffUtil.ItemCallback<Api.ConversationMessage>() {
    override fun areItemsTheSame(oldItem: Api.ConversationMessage, newItem: Api.ConversationMessage): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Api.ConversationMessage, newItem: Api.ConversationMessage): Boolean {
        return oldItem == newItem
    }
}