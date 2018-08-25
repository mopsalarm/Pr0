package com.pr0gramm.app.ui

import android.content.Context
import android.os.Bundle
import android.support.annotation.StringRes
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter

/**
 */
class TabsAdapter(private val context: Context, fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager) {
    private val tabs: MutableList<TabInfo> = mutableListOf()

    fun <T : Fragment> addTab(@StringRes titleId: Int, cls: Class<T>, args: Bundle? = null) {
        val title = context.getString(titleId)
        val info = TabInfo(title, cls, args)
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
        return Fragment.instantiate(context, info.cls.name, info.args)
    }

    private class TabInfo(val title: String, val cls: Class<out Fragment>, val args: Bundle?)
}
