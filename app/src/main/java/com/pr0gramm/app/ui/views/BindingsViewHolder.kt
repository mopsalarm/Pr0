package com.pr0gramm.app.ui.views

import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

data class BindingsViewHolder<out B : ViewBinding>(val bindings: B) : RecyclerView.ViewHolder(bindings.root)