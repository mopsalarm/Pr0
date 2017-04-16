package com.pr0gramm.app.ui.fragments

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.salomonbrys.kodein.instance
import com.google.common.base.Optional
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.*
import com.pr0gramm.app.ui.FilterFragment
import com.pr0gramm.app.ui.IdFragmentStatePagerAdapter
import com.pr0gramm.app.ui.MainActionHandler
import com.pr0gramm.app.ui.PreviewInfo
import com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.util.AndroidUtility
import kotterknife.bindView
import org.slf4j.LoggerFactory
import rx.functions.Action1

/**
 */
class PostPagerFragment : BaseFragment(), FilterFragment, PostPagerNavigation, PreviewInfoSource {
    private val feedService: FeedService by instance()

    private val viewPager: ViewPager by bindView(R.id.pager)

    private lateinit var feed: Feed
    private lateinit var adapter: PostAdapter

    private var activePostFragment: PostFragment? = null
    private var previewInfo: PreviewInfo? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_post_pager, container, false)
    }

    override fun injectComponent(activityComponent: ActivityComponent) {
        activityComponent.inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // get the feed to show and setup a loader to load more data
        feed = getArgumentFeed(savedInstanceState)
        val loader = FeedLoader(
                FeedLoader.bindTo(bindToLifecycleAsync<Any>(), Action1 { AndroidUtility.logToCrashlytics(it) }),
                feedService, feed)

        // create the adapter on the view
        adapter = PostAdapter(feed, loader)

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

        val fancyScroll = Settings.get().useTextureView
        if (fancyScroll) {
            viewPager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                    if (position >= 0 && position + 1 < adapter.count) {
                        val prev = adapter.getFragment(position).orNull()
                        val next = adapter.getFragment(position + 1).orNull()
                        if (prev is PostFragment && next is PostFragment) {
                            val offset = positionOffsetPixels / 2
                            prev.mediaHorizontalOffset(offset)
                            next.mediaHorizontalOffset(offset - viewPager.width / 2)
                        }
                    }
                }
            })
        }

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

    private fun makeItemCurrent(item: FeedItem) {
        val index = feed.indexOf(item).or(0)

        logger.info("Moving to index: " + index)
        viewPager.setCurrentItem(index, false)
    }

    internal fun updateActiveItem(newActiveFragment: PostFragment) {
        val position = adapter.getItemPosition(newActiveFragment)
        if (activePostFragment === newActiveFragment)
            return

        logger.info("Setting feed item activate at $position to $newActiveFragment")

        // deactivate previous item
        activePostFragment?.setActive(false)

        // and activate the next one
        activePostFragment = newActiveFragment.also { fragment ->
            fragment.setActive(true)

            // try scroll to initial comment. This will only work if the comment
            // is a part of the given post and will otherwise do nothing
            val startCommentId = arguments.getLong(ARG_START_ITEM_COMMENT)
            if (startCommentId > 0) {
                fragment.autoScrollToComment(startCommentId)
            }
        }
    }

    override fun previewInfoFor(item: FeedItem): PreviewInfo? {
        return previewInfo?.takeIf { it.itemId == item.id }
    }

    /**
     * Gets the feed from the saved state. If there is no state
     * or it does not contain the feed proxy, the feed proxy is extracted
     * from [.getArguments]

     * @param savedState An optional saved state.
     */
    private fun getArgumentFeed(savedState: Bundle?): Feed {
        val encoded: Bundle = when {
            savedState != null -> savedState.getBundle(ARG_FEED_PROXY)
            arguments != null -> arguments.getBundle(ARG_FEED_PROXY)
            else -> throw IllegalStateException("No feed-proxy found.")
        }

        return Feed.restore(encoded)
    }

    /**
     * @see .getArgumentFeed
     */
    private fun getArgumentStartItem(savedState: Bundle?): FeedItem {
        return when {
            savedState != null -> savedState.getParcelable(ARG_START_ITEM)
            arguments != null -> arguments.getParcelable(ARG_START_ITEM)
            else -> throw IllegalStateException("No feed-proxy found.")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        saveStateToBundle(outState)
    }

    /**
     * Returns the feed filter for this fragment.
     */
    override // prevent errors here
    val currentFilter: FeedFilter
        get() {
            if (feed == null)
                return FeedFilter()

            return feed.feedFilter
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
        val position = viewPager.currentItem
        val item = feed.at(position)
        outState.putParcelable(ARG_START_ITEM, item)
        outState.putParcelable(ARG_FEED_PROXY, feed.persist(position))
    }

    /**
     * Sets the pixels that should be used in the transition.
     */
    fun setPreviewInfo(previewInfo: PreviewInfo) {
        this.previewInfo = previewInfo
    }

    override fun moveToNext() {
        val newIndex = viewPager.currentItem + 1
        if (newIndex < viewPager.adapter.count)
            viewPager.currentItem = newIndex
    }

    override fun moveToPrev() {
        val newIndex = viewPager.currentItem - 1
        if (newIndex >= 0)
            viewPager.currentItem = newIndex
    }

    private inner class PostAdapter(private val proxy: Feed, private val loader: FeedLoader) : IdFragmentStatePagerAdapter(childFragmentManager), Feed.FeedListener {

        init {
            proxy.setFeedListener(this)
        }

        override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
            super.setPrimaryItem(container, position, `object`)
            updateActiveItem(`object` as PostFragment)
            saveStateToBundle(arguments)
        }

        override fun getItem(position: Int): Fragment {
            var position = position
            if (!loader.isLoading) {
                if (position > proxy.size() - 12) {
                    logger.info("requested pos=$position, load next page")
                    loader.next()
                }

                if (position < 12) {
                    logger.info("requested pos=$position, load prev page")
                    loader.previous()
                }
            }

            // check that we dont exceed the range.
            position = Math.min(proxy.size() - 1, Math.max(0, position))
            return PostFragment.newInstance(proxy.at(position))
        }

        override fun getCount(): Int {
            return proxy.size()
        }

        override fun getItemPosition(`object`: Any?): Int {
            val item = (`object` as PostFragment).getFeedItem()
            return proxy.indexOf(item).or(PagerAdapter.POSITION_NONE)
        }

        override fun getItemId(position: Int): Long {
            return proxy.at(position).id()
        }

        override fun onNewItems(newItems: List<FeedItem>) {
            notifyDataSetChanged()
        }

        override fun onRemoveItems() {
            // should not happen
            throw UnsupportedOperationException()
        }

        override fun onWrongContentType() {
            // ignore
        }
    }

    companion object {
        internal val logger = LoggerFactory.getLogger("PostPagerFragment")

        private val ARG_FEED_PROXY = "PostPagerFragment.feedProxy"
        private val ARG_START_ITEM = "PostPagerFragment.startItem"
        private val ARG_START_ITEM_COMMENT = "PostPagerFragment.startItemComment"

        fun newInstance(feed: Feed, idx: Int, commentId: Optional<Long>): PostPagerFragment {
            val arguments = Bundle()
            arguments.putBundle(ARG_FEED_PROXY, feed.persist(idx))
            arguments.putParcelable(ARG_START_ITEM, feed.at(idx))
            arguments.putLong(ARG_START_ITEM_COMMENT, commentId.or(-1L))

            val fragment = PostPagerFragment()
            fragment.arguments = arguments
            return fragment
        }
    }

}
