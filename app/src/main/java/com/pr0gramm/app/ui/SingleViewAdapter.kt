package com.pr0gramm.app.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes

/**
 */
abstract class SingleViewAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = 1

    override fun getItemId(position: Int): Long = 1

    override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
        // do nothing
    }

    class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view)

    companion object {
        fun ofLayout(@LayoutRes layoutId: Int): SingleViewAdapter {
            return of { context -> View.inflate(context, layoutId, null) }
        }

        inline fun of(crossinline factory: (Context) -> View): SingleViewAdapter {
            return object : SingleViewAdapter() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                    return ViewHolder(factory(parent.context))
                }
            }
        }
    }
}
