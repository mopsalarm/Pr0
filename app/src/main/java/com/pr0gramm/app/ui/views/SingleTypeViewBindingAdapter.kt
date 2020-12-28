package com.pr0gramm.app.ui.views

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.viewbinding.ViewBinding
import com.pr0gramm.app.ui.AsyncListAdapter
import com.pr0gramm.app.util.layoutInflater

class SingleTypeViewBindingAdapter<T : Any, B : ViewBinding>(
        private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> B,
        initialItems: List<T> = listOf(),
        diffCallback: DiffUtil.ItemCallback<T> = InstanceDiffCallback(),
        detectMoves: Boolean = false,
        private val bind: (BindingsViewHolder<B>, T) -> Unit,
) : AsyncListAdapter<T, BindingsViewHolder<B>>(diffCallback, detectMoves) {

    init {
        submitList(initialItems)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingsViewHolder<B> {
        return BindingsViewHolder(this.inflate(parent.layoutInflater, parent, false))
    }

    override fun onBindViewHolder(holder: BindingsViewHolder<B>, position: Int) {
        bind(holder, getItem(position))
    }
}
