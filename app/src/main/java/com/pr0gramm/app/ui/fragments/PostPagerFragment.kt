package com.pr0gramm.app.ui.fragments

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.databinding.FragmentPostPagerBinding
import com.pr0gramm.app.feed.*
import com.pr0gramm.app.parcel.getParcelableOrNull
import com.pr0gramm.app.ui.*
import com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindViews
import com.pr0gramm.app.ui.base.launchUntilViewDestroy
import com.pr0gramm.app.ui.fragments.feed.FeedFragment
import com.pr0gramm.app.ui.fragments.post.PostFragment
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.observeChangeEx
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance

/**
 */
class PostPagerFragment : BaseFragment("PostPagerFragment", R.layout.fragment_post_pager), FilterFragment, TitleFragment, PreviewInfoSource {
    private val feedService: FeedService by instance()

    private val views by bindViews(FragmentPostPagerBinding::bind)

    private lateinit var adapter: PostAdapter

    private var previewInfo: PreviewInfo? = null

    private var latestActivePostFragment: PostFragment? = null

    // to prevent double saving of current state
    private var lastSavedPosition: Int = -1

    private var initialCommentRef: CommentRef? = null

    private val settings = Settings.get()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initialCommentRef = arguments?.getParcelable(ARG_START_ITEM_COMMENT_REF)

        // Listen for changes in fragments. We use this, because with the ViewPager2
        // callbacks we don't have any access to the fragment, and we don't even know,
        // when the fragment will be there and available.
        val l = object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                logger.debug { "Fragment was resumed." }
                if (f is PostFragment && latestActivePostFragment !== f) {
                    latestActivePostFragment = f
                    onActiveFragmentChanged()
                }
            }
        }

        childFragmentManager.registerFragmentLifecycleCallbacks(l, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // get the feed to show and setup a loader to load more data
        val previousFeed = getArgumentFeed(savedInstanceState)
        val manager = FeedManager(viewLifecycleOwner.lifecycleScope, feedService, previousFeed)

        // create the adapter on the view
        adapter = PostAdapter(manager, previousFeed)

        launchUntilViewDestroy {
            manager.updates.filterIsInstance<FeedManager.Update.NewFeed>().collect { update ->
                adapter.feed = update.feed
            }
        }

        if (activity is ToolbarActivity) {
            val activity = activity as ToolbarActivity
            activity.scrollHideToolbarListener.reset()

            views.pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrollStateChanged(state: Int) {
                    activity.scrollHideToolbarListener.reset()
                    latestActivePostFragment?.exitFullscreen()
                }

                override fun onPageSelected(position: Int) {
                    logger.info { "Page was selected: $position" }

                    val fragment = adapter.getFragment(position)
                    if (fragment != null && fragment != latestActivePostFragment) {
                        childFragmentManager.commitNow(allowStateLoss = true) {
                            val previousFragment = latestActivePostFragment
                            if (previousFragment != null && previousFragment.isAdded) {
                                setMaxLifecycle(previousFragment, Lifecycle.State.STARTED)
                            }

                            setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
                        }
                    }
                }

                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            })
        }

        if (Settings.get().fancyScrollHorizontal) {
            views.pager.setPageTransformer(false) { page: View, position: Float ->
                val viewer = page.findViewWithTag<View>(PostFragment.ViewerTag)
                if (viewer != null) {
                    viewer.translationX = -(position * page.width / 2.0f)
                }
            }
        }

        views.pager.adapter = adapter
        views.pager.offscreenPageLimit = 1

        // calculate index of the first item to show if this is the first
        // time we show this fragment.
        makeItemCurrent(getArgumentStartItem(savedInstanceState))
    }

    private fun onActiveFragmentChanged() {
        val mainActivity = activity as? MainActivity
        mainActivity?.updateActionbarTitle()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        val item = getArgumentStartItem(savedInstanceState)

        if (adapter.feed.getOrNull(views.pager.currentItem)?.id != item.id) {
            // the position of the view pager might have been saved. We need to re-save it now.
            logger.info { "Going to restore view position in onViewStateRestored" }
            makeItemCurrent(item)
        }
    }

    override fun onStop() {
        latestActivePostFragment?.feedItem?.let { feedItem ->
            val feed = adapter.feed
            if (feedItem in feed) {
                val target = targetFragment as? FeedFragment
                        ?: return@let
                target.updateFeedItemTarget(adapter.feed, feedItem)
            }
        }

        super.onStop()
    }

    private fun makeItemCurrent(item: FeedItem) {
        val index = adapter.feed.indexById(item.id) ?: 0

        logger.info { "Restore feed at index: $index (${item.id})" }
        views.pager.setCurrentItem(index, false)
    }

    override fun previewInfoFor(item: FeedItem): PreviewInfo? {
        return previewInfo?.takeIf { it.itemId == item.id }
    }

    /**
     * Get the feed from the given bundle.
     */
    private fun getArgumentFeed(savedState: Bundle?): Feed {
        val parceled = savedState?.getParcelableOrNull<Feed.FeedParcel>(ARG_FEED)
                ?: arguments?.getParcelableOrNull<Feed.FeedParcel>(ARG_FEED)
                ?: throw IllegalStateException("No feed found.")

        return parceled.feed
    }

    /**
     * @see getArgumentFeed
     */
    private fun getArgumentStartItem(savedState: Bundle?): FeedItem {
        return savedState?.getParcelableOrNull<FeedItem>(ARG_START_ITEM)
                ?: arguments?.getParcelableOrNull<FeedItem>(ARG_START_ITEM)
                ?: throw IllegalStateException("No initial item found.")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        saveStateToBundle(outState)
    }

    /**
     * Returns the feed filter for this fragment.
     */
    override val currentFilter: FeedFilter
        get() = adapter.feed.filter

    override val title: TitleFragment.Title?
        get() = buildFragmentTitle()

    private fun buildFragmentTitle(): TitleFragment.Title? {
        val titleOverride = arguments?.getString(ARG_TITLE)

        if (settings.useTopTagAsTitle) {
            // fetch title from the current active post fragment
            var title = latestActivePostFragment?.title

            if (titleOverride != null) {
                // if the caller gave us a more specific title, we'll use that one.
                title = title?.copy(title = titleOverride) ?: TitleFragment.Title(titleOverride)
            }

            return title
        } else {
            // build one from the current filter
            val context = this.context ?: return null
            return FeedFilterFormatter.toTitle(context, currentFilter)
        }
    }

    fun onTagClicked(tag: Api.Tag) {
        val handler = activity as MainActionHandler
        handler.onFeedFilterSelected(currentFilter.withTags(tag.text))
    }

    fun onUsernameClicked(username: String) {
        // always show all uploads of a user.
        val newFilter = currentFilter
                .withFeedType(FeedType.NEW)
                .withUser(username)

        (activity as MainActionHandler).onFeedFilterSelected(newFilter)
    }

    private fun saveStateToBundle(outState: Bundle) {
        if (view != null && adapter.feed.isNotEmpty()) {
            val position = views.pager.currentItem.coerceIn(adapter.feed.indices)

            if (lastSavedPosition != position) {
                lastSavedPosition = position

                val item = adapter.feed[position]
                outState.putParcelable(ARG_START_ITEM, item)
                outState.putParcelable(ARG_FEED, adapter.feed.parcelAround(position))

                logger.debug { "Saved $position (id=${item.id})" }
            }
        }
    }

    /**
     * Sets the pixels that should be used in the transition.
     */
    fun setPreviewInfo(previewInfo: PreviewInfo) {
        this.previewInfo = previewInfo
    }

    private inner class PostAdapter(
            private val manager: FeedManager,
            feed: Feed)
        : IdFragmentStatePagerAdapter(childFragmentManager) {

        var feed: Feed by observeChangeEx(feed) { oldValue, newValue ->
            notifyDataSetChanged()
        }

        override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
            super.setPrimaryItem(container, position, `object`)
            arguments?.let { args -> saveStateToBundle(args) }
        }

        override fun getItem(position: Int): Fragment {
            if (!manager.isLoading) {
                if (position > feed.size - 12) {
                    logger.debug { "Requested pos=$position, load next page" }
                    manager.next()
                }

                if (position < 12) {
                    logger.debug { "Requested pos=$position, load prev page" }
                    manager.previous()
                }
            }

            // build a new fragment from the given item.
            val capped = position.coerceIn(0, feed.size - 1)
            val item = feed[capped]

            // initialize with reference to comment
            val initialCommentRef = initialCommentRef
            if (initialCommentRef?.itemId == item.id) {
                this@PostPagerFragment.initialCommentRef = null
                return PostFragment.newInstance(item, initialCommentRef)
            }

            return PostFragment.newInstance(item)
        }

        override fun getCount(): Int {
            return feed.size
        }

        override fun getItemPosition(`object`: Any): Int {
            val item = (`object` as PostFragment).feedItem
            return feed.indexById(item.id) ?: POSITION_NONE
        }

        override fun getItemId(position: Int): Long {
            return feed[position].id
        }
    }

    companion object {
        private const val ARG_FEED = "PP.feed"
        private const val ARG_TITLE = "PP.title"
        private const val ARG_START_ITEM = "PP.startItem"
        private const val ARG_START_ITEM_COMMENT_REF = "PP.startItemComment"

        fun newInstance(feed: Feed, idx: Int, commentRef: CommentRef?, fragmentTitle: String?): PostPagerFragment {
            return PostPagerFragment().arguments {
                putParcelable(ARG_FEED, feed.parcelAround(idx))
                putParcelable(ARG_START_ITEM, feed[idx])
                putParcelable(ARG_START_ITEM_COMMENT_REF, commentRef)
                putString(ARG_TITLE, fragmentTitle)
            }
        }
    }
}
