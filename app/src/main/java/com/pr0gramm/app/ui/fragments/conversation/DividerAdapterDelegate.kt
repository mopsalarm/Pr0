package com.pr0gramm.app.ui.fragments.conversation

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.ui.ListItemTypeAdapterDelegate
import com.pr0gramm.app.ui.adapters2.BindableViewHolder

class DividerAdapterDelegate : ListItemTypeAdapterDelegate<String, String, RecyclerView.ViewHolder>() {
    private val f = ConversationItemDividerViewHolder

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        return f.createViewHolder(parent)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, value: String) {
        val vh = holder as BindableViewHolder<ConversationItem.Divider>
        vh.bindTo(ConversationItem.Divider(value))
    }
}