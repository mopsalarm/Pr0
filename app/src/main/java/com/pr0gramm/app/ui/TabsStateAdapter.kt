package com.pr0gramm.app.ui

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 */
class TabsStateAdapter(private val activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int {
        return tabs.size
    }

    override fun createFragment(position: Int): Fragment {
        val info = tabs[position]
        return info.fragmentConstructor().apply {
            arguments = info.args
        }
    }

    private val tabs: MutableList<TabInfo> = mutableListOf()

    fun addTab(@StringRes titleId: Int, args: Bundle? = null, fragmentConstructor: () -> Fragment) {
        val title = activity.getString(titleId)
        val info = TabInfo(title, fragmentConstructor, args)
        tabs.add(info)

        notifyDataSetChanged()
    }

    fun getPageTitle(position: Int): CharSequence {
        return tabs[position].title
    }

    private class TabInfo(val title: String, val fragmentConstructor: () -> Fragment, val args: Bundle?)
}
