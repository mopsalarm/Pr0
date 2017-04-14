package com.pr0gramm.app.ui.fragments

import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.ui.FilterFragment
import com.pr0gramm.app.ui.ScrollHideToolbarListener
import com.pr0gramm.app.ui.TabsAdapter
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.arguments
import kotterknife.bindView

/**
 */
class FavoritesFragment : BaseFragment(), FilterFragment {
    private val pager: ViewPager by bindView(R.id.favorites_pager)
    private val tabLayout: TabLayout by bindView(R.id.tabs)

    private lateinit var feedFilter: FeedFilter

    override fun injectComponent(activityComponent: ActivityComponent) {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // build the filter for this view
        val username = arguments.getString(ARG_USERNAME, "")
        feedFilter = FeedFilter().withFeedType(FeedType.NEW).withLikes(username)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_favorites, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fixViewTopOffset(view)
        resetToolbar()

        val feedFragmentArguments = FeedFragment.newArguments(feedFilter, false, null, null)

        pager.adapter = TabsAdapter(context, childFragmentManager).apply {
            addTab(R.string.action_favorites, FeedFragment::class.java, feedFragmentArguments)
            addTab(R.string.action_kfav, FavedCommentFragment::class.java)
        }

        tabLayout.setupWithViewPager(pager)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return true
    }

    private fun resetToolbar() {
        val activity = activity
        if (activity is ScrollHideToolbarListener.ToolbarActivity) {
            val listener = activity.scrollHideToolbarListener
            listener.reset()
        }
    }

    private fun fixViewTopOffset(view: View) {
        val params = view.layoutParams
        if (params is ViewGroup.MarginLayoutParams) {
            params.topMargin = AndroidUtility.getActionBarContentOffset(context)
            view.layoutParams = params
        }
    }

    override fun getCurrentFilter(): FeedFilter {
        return feedFilter
    }

    companion object {
        private val ARG_USERNAME = "FavoritesFragment.username"

        @JvmStatic
        fun newInstance(username: String): FavoritesFragment {
            return FavoritesFragment().arguments {
                putString(ARG_USERNAME, username)
            }
        }
    }
}
