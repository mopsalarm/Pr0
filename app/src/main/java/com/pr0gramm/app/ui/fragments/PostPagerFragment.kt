package com.pr0gramm.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.ViewPager
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.*
import com.pr0gramm.app.parcel.getFreezable
import com.pr0gramm.app.parcel.putFreezable
import com.pr0gramm.app.ui.*
import com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindView
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.observeChange

/**
 */
class PostPagerFragment : BaseFragment("PostPagerFragment"), FilterFragment, TitleFragment, PostPagerNavigation, PreviewInfoSource {
    private val feedService: FeedService by instance()

    private val viewPager: ViewPager by bindView(R.id.pager)

    private lateinit var adapter: PostAdapter

    private var activePostFragment: PostFragment? = null
    private var previewInfo: PreviewInfo? = null

    // to prevent double saving of current state
    private var lastSavedPosition: Int = -1

    private var initialCommentRef: CommentRef? = null

    private val settings = Settings.get()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initialCommentRef = arguments?.getParcelable(ARG_START_ITEM_COMMENT_REF)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_post_pager, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // get the feed to show and setup a loader to load more data
        val previousFeed = getArgumentFeed(savedInstanceState)
        val manager = FeedManager(feedService, previousFeed)

        // create the adapter on the view
        adapter = PostAdapter(previousFeed, manager)

        manager.updates
                .bindToLifecycle()
                .ofType(FeedManager.Update.NewFeed::class.java)
                .subscribe { adapter.feed = it.feed }

        if (activity is ToolbarActivity) {
            val activity = activity as ToolbarActivity
            activity.scrollHideToolbarListener.reset()

            viewPager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
                override fun onPageScrollStateChanged(state: Int) {
                    activity.scrollHideToolbarListener.reset()
                    activePostFragment?.exitFullscreen()
                }
            })
        }

        viewPager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                if (position >= 0 && position + 1 < adapter.count) {
                    val prev = adapter.getFragment(position)
                    val next = adapter.getFragment(position + 1)
                    if (prev is PostFragment && next is PostFragment && settings.fancyScrollHorizontal) {
                        val offset = positionOffsetPixels / 2
                        prev.mediaHorizontalOffset(offset)
                        next.mediaHorizontalOffset(offset - viewPager.width / 2)
                    }
                }
            }
        })

        viewPager.adapter = adapter

        if (savedInstanceState != null) {
            // calculate index of the first item to show if this is the first
            // time we show this fragment.
            val start = getArgumentStartItem(savedInstanceState)
            makeItemCurrent(start)
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        // calculate index of the first item to show if this is the first
        // time we show this fragment.
        val start = getArgumentStartItem(savedInstanceState)
        makeItemCurrent(start)
    }

    override fun onStop() {
        activePostFragment?.feedItem?.let { feedItem ->
            val feed = adapter.feed
            if (feedItem in feed) {
                val target = targetFragment as? FeedFragment ?: return@let
                target.updateFeedItemTarget(adapter.feed, feedItem)
            }
        }

        super.onStop()
    }

    private fun makeItemCurrent(item: FeedItem) {
        val index = adapter.feed.indexById(item.id) ?: 0

        logger.info { "Moving to index: $index" }
        viewPager.setCurrentItem(index, false)
    }

    internal fun updateActiveItem(newActiveFragment: PostFragment) {
        val position = adapter.getItemPosition(newActiveFragment)
        if (activePostFragment === newActiveFragment)
            return

        logger.info { "Setting feed item activate at $position to $newActiveFragment" }

        // deactivate previous item
        activePostFragment?.setActive(false)

        // and activate the next one
        activePostFragment = newActiveFragment.also { fragment ->
            fragment.setActive(true)
        }

        val mainActivity = activity as? MainActivity
        mainActivity?.updateActionbarTitle()
    }

    override fun previewInfoFor(item: FeedItem): PreviewInfo? {
        return previewInfo?.takeIf { it.itemId == item.id }
    }

    /**
     * Gets the feed from the saved state. If there is no state
     * or it does not contain the feed proxy, the feed proxy is extracted
     * from [getArguments]
     *
     * @param savedState An optional saved state.
     */
    private fun getArgumentFeed(savedState: Bundle?): Feed {
        val parceled = savedState?.getFreezable(ARG_FEED, Feed.FeedParcel)
                ?: arguments?.getFreezable(ARG_FEED, Feed.FeedParcel)
                ?: throw IllegalStateException("No feed found.")

        return parceled.feed
    }

    /**
     * @see .getArgumentFeed
     */
    private fun getArgumentStartItem(savedState: Bundle?): FeedItem {
        return savedState?.getFreezable(ARG_START_ITEM, FeedItem)
                ?: arguments?.getFreezable(ARG_START_ITEM, FeedItem)
                ?: throw IllegalStateException("No initial item found.")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (view != null) {
            saveStateToBundle(outState)
        }
    }

    /**
     * Returns the feed filter for this fragment.
     */
    override val currentFilter: FeedFilter
        get() = adapter.feed.filter

    override val title: TitleFragment.Title?
        get() {
            val title = activePostFragment?.title

            // if the caller gave us a more specific title, we'll use that one.
            val override = arguments?.getString(ARG_TITLE)
            if (override != null)
                return title?.copy(subTitle = override) ?: TitleFragment.Title(override)

            return title
        }

    fun onTagClicked(tag: Api.Tag) {
        (activity as MainActionHandler).onFeedFilterSelected(
                currentFilter.withTags(tag.tag))
    }

    fun onUsernameClicked(username: String) {
        // always show all uploads of a user.
        val newFilter = currentFilter
                .withFeedType(FeedType.NEW)
                .withUser(username)

        (activity as MainActionHandler).onFeedFilterSelected(newFilter)
    }

    internal fun saveStateToBundle(outState: Bundle) {
        if (adapter.feed.isNotEmpty()) {
            val position = viewPager.currentItem.coerceIn(adapter.feed.indices)

            if (lastSavedPosition != position) {
                val item = adapter.feed[position]
                outState.putFreezable(ARG_START_ITEM, item)
                outState.putFreezable(ARG_FEED, adapter.feed.parcelAround(position))

                lastSavedPosition = position
            }
        }
    }

    /**
     * Sets the pixels that should be used in the transition.
     */
    fun setPreviewInfo(previewInfo: PreviewInfo) {
        this.previewInfo = previewInfo
    }

    override fun moveToNext() {
        viewPager.adapter?.let { adapter ->
            val newIndex = viewPager.currentItem + 1
            if (newIndex < adapter.count)
                viewPager.currentItem = newIndex
        }
    }

    override fun moveToPrev() {
        val newIndex = viewPager.currentItem - 1
        if (newIndex >= 0)
            viewPager.currentItem = newIndex
    }

    private inner class PostAdapter(feed: Feed, val manager: FeedManager) : IdFragmentStatePagerAdapter(childFragmentManager) {
        var feed: Feed by observeChange(feed) {
            notifyDataSetChanged()
        }

        override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
            super.setPrimaryItem(container, position, `object`)
            updateActiveItem(`object` as PostFragment)

            if (view != null) {
                arguments?.let { args ->
                    saveStateToBundle(args)
                }
            }
        }

        override fun getItem(position: Int): androidx.fragment.app.Fragment {
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
            return feed.indexById(item.id) ?: androidx.viewpager.widget.PagerAdapter.POSITION_NONE
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
                putFreezable(ARG_FEED, feed.parcelAround(idx))
                putFreezable(ARG_START_ITEM, feed[idx])
                putParcelable(ARG_START_ITEM_COMMENT_REF, commentRef)
                putString(ARG_TITLE, fragmentTitle)
            }
        }
    }
}
