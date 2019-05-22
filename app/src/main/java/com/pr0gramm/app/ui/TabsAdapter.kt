package com.pr0gramm.app.ui

import android.content.Context
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter

/**
 */
class TabsAdapter(private val context: Context, fragmentManager: androidx.fragment.app.FragmentManager) : FragmentPagerAdapter(fragmentManager) {
    private val tabs: MutableList<TabInfo> = mutableListOf()

    fun addTab(@StringRes titleId: Int, args: Bundle? = null, fragmentConstructor: () -> Fragment) {
        val title = context.getString(titleId)
        val info = TabInfo(title, fragmentConstructor, args)
        tabs.add(info)

        notifyDataSetChanged()
    }

    override fun getPageTitle(position: Int): CharSequence {
        return tabs[position].title
    }

    override fun getCount(): Int {
        return tabs.size
    }

    override fun getItem(position: Int): Fragment {
        val info = tabs[position]

        return info.fragmentConstructor().apply {
            arguments = info.args
        }
    }

    private class TabInfo(val title: String, val fragmentConstructor: () -> Fragment, val args: Bundle?)
}
