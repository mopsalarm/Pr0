package com.pr0gramm.app.ui

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentPagerAdapter

/**
 */
class TabsStateAdapter : FragmentPagerAdapter {
    constructor(context: Context, fragment: Fragment) : super(fragment.childFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        this.context = context
    }

    constructor(activity: FragmentActivity) : super(activity.supportFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        this.context = activity
    }

    private val context: Context
    private val tabs: MutableList<TabInfo> = mutableListOf()

    fun getItemCount(): Int {
        return tabs.size
    }

    private fun createFragment(position: Int): Fragment {
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

    fun addTab(title: String, args: Bundle? = null, id: Any? = null, fragmentConstructor: () -> Fragment) {
        addTab(TabInfo(title, fragmentConstructor, args, id))
    }

    private fun addTab(tab: TabInfo) {
        tabs += tab
        notifyDataSetChanged()
    }

    fun updateTabTitle(id: Any, title: String) {
        for ((idx, tab) in tabs.withIndex()) {
            if (tab.id == id && tab.title != title) {
                tabs[idx] = tab.copy(title = title)
                notifyDataSetChanged()
            }
        }
    }

    override fun getItem(position: Int): Fragment {
        return createFragment(position)
    }

    override fun getCount(): Int {
        return tabs.size
    }

    override fun getPageTitle(position: Int): CharSequence {
        return tabs[position].title
    }

    fun replaceTabs(tabs: List<TabInfo>) {
        this.tabs.clear()
        this.tabs += tabs
        notifyDataSetChanged()
    }

    data class TabInfo(val title: String, val fragmentConstructor: () -> Fragment, val args: Bundle? = null, val id: Any? = null)
}
