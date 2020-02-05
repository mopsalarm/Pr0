package com.pr0gramm.app.ui

import android.content.Context
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 */
class TabsStateAdapter : FragmentStateAdapter {
    constructor(context: Context, fragment: Fragment) : super(fragment) {
        this.context = context
    }

    constructor(activity: FragmentActivity) : super(activity) {
        this.context = activity
    }

    private val context: Context
    private val tabs: MutableList<TabInfo> = mutableListOf()

    override fun getItemCount(): Int {
        return tabs.size
    }

    override fun createFragment(position: Int): Fragment {
        val info = tabs[position]

        return info.fragmentConstructor().also { fr ->
            if (info.args != null) {
                val existing = fr.arguments
                if (existing != null) {
                    existing.putAll(info.args)
                } else {
                    fr.arguments = Bundle(info.args)
                }
            }
        }
    }

    fun addTab(@StringRes titleId: Int, args: Bundle? = null, fragmentConstructor: () -> Fragment) {
        val title = context.getString(titleId)
        val info = TabInfo(title, fragmentConstructor, args)
        tabs.add(info)

        notifyDataSetChanged()
    }

    fun getPageTitle(position: Int): CharSequence {
        return tabs[position].title
    }

    private class TabInfo(val title: String, val fragmentConstructor: () -> Fragment, val args: Bundle?)
}
