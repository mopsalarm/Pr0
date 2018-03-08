package com.pr0gramm.app.ui

import android.content.Context
import android.support.annotation.LayoutRes
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup

/**
 */
abstract class SingleViewAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = 1

    override fun getItemId(position: Int): Long = 1

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // do nothing
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    companion object {
        fun <V : View> ofView(view: V): SingleViewAdapter = of { _ -> view }

        fun ofLayout(@LayoutRes layoutId: Int): SingleViewAdapter {
            return of { context -> View.inflate(context, layoutId, null) }
        }

        inline fun of(crossinline factory: (Context) -> View): SingleViewAdapter {
            return object : SingleViewAdapter() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    return ViewHolder(factory(parent.context))
                }
            }
        }
    }
}
