package com.pr0gramm.app.ui.fragments.favorites

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.pr0gramm.app.R
import com.pr0gramm.app.databinding.FragmentCollectionsBinding
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.ui.FilterFragment
import com.pr0gramm.app.ui.ScrollHideToolbarListener
import com.pr0gramm.app.ui.TabsStateAdapter
import com.pr0gramm.app.ui.TitleFragment
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindViews
import com.pr0gramm.app.ui.base.launchInViewScope
import com.pr0gramm.app.ui.fragments.FavedCommentFragment
import com.pr0gramm.app.ui.fragments.feed.FeedFragment
import com.pr0gramm.app.ui.viewModels
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.getStringOrThrow

/**
 */
class CollectionsFragment : BaseFragment("CollectionsFragment", R.layout.fragment_collections), FilterFragment, TitleFragment {
    private val views by bindViews(FragmentCollectionsBinding::bind)

    private val model by viewModels {
        FavoritesViewModel(
                user = requireArguments().getStringOrThrow(ARG_USERNAME),
                userService = instance(),
                collectionsService = instance(),
        )
    }

    override var title: TitleFragment.Title? = null

    override var currentFilter = FeedFilter().withFeedType(FeedType.NEW)
        private set

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fixViewTopOffset(view)
        resetToolbar()

        title = TitleFragment.Title(
                title = getString(R.string.action_collections),
                subtitle = model.user,
        )

        currentFilter = currentFilter.basicWithCollection(model.user, "**ANY", "**ANY")

        val tabsAdapter = TabsStateAdapter(requireContext(), this)

        launchInViewScope {
            model.collectionsState.collect { collections ->
                val tabs = collections.mapTo(ArrayList()) { collection ->
                    val filter = FeedFilter()
                        .withFeedType(FeedType.NEW)
                        .basicWithCollection(model.user, collection)

                    TabsStateAdapter.TabInfo(
                            id = collection.id,
                            title = collection.titleWithOwner,
                            fragmentConstructor = ::FeedFragment,
                            args = FeedFragment.newEmbedArguments(filter),
                    )
                }

                if (model.myView) {
                    tabs += TabsStateAdapter.TabInfo(
                            title = getString(R.string.action_kfav),
                            fragmentConstructor = ::FavedCommentFragment,
                    )
                }

                tabsAdapter.replaceTabs(tabs)
            }
        }

        views.favoritesPager.adapter = tabsAdapter
        views.favoritesPager.offscreenPageLimit = 2

        views.tabs.setupWithViewPager(views.favoritesPager)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
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
            params.topMargin = AndroidUtility.getActionBarContentOffset(view.context)
            view.layoutParams = params
        }
    }

    companion object {
        const val ARG_USERNAME = "FavoritesFragment.username"

        fun newInstance(username: String): CollectionsFragment {
            return CollectionsFragment().arguments {
                putString(ARG_USERNAME, username)
            }
        }
    }
}
