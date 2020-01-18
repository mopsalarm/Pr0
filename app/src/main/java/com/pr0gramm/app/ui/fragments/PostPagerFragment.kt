package com.pr0gramm.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.collection.LongSparseArray
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.*
import com.pr0gramm.app.parcel.getFreezable
import com.pr0gramm.app.parcel.putFreezable
import com.pr0gramm.app.time
import com.pr0gramm.app.ui.*
import com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindView
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.debug
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.observeChangeEx

/**
 */
class PostPagerFragment : BaseFragment("PostPagerFragment"), FilterFragment, TitleFragment, PreviewInfoSource {
    private val feedService: FeedService by instance()

    private val viewPager: ViewPager2 by bindView(R.id.pager)

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
        // callbacks we dont have any access to the fragment, and we don't even know,
        // when the fragment will be there and available.
        val l = object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                if (f is PostFragment && latestActivePostFragment !== f) {
                    latestActivePostFragment = f
                    onActiveFragmentChanged()
                }
            }
        }

        childFragmentManager.registerFragmentLifecycleCallbacks(l, false)
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

            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrollStateChanged(state: Int) {
                    activity.scrollHideToolbarListener.reset()
                    latestActivePostFragment?.exitFullscreen()
                }

                override fun onPageSelected(position: Int) {
                    arguments?.let { saveStateToBundle(it) }
                }
            })
        }

        if (Settings.get().fancyScrollHorizontal) {
            viewPager.setPageTransformer { page: View, position: Float ->
                val viewer = page.findViewWithTag<View>(PostFragment.ViewerTag)
                if (viewer != null) {
                    viewer.translationX = -(position * page.width / 2.0f)
                }
            }
        }

        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = 1

        if (savedInstanceState != null) {
            // calculate index of the first item to show if this is the first
            // time we show this fragment.
            val start = getArgumentStartItem(savedInstanceState)
            makeItemCurrent(start)
        }
    }

    private fun onActiveFragmentChanged() {
        val mainActivity = activity as? MainActivity
        mainActivity?.updateActionbarTitle()

        if (::adapter.isInitialized) {
            adapter.cleanupSavedStates()
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
        latestActivePostFragment?.feedItem?.let { feedItem ->
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
        saveStateToBundle(outState)
    }

    /**
     * Returns the feed filter for this fragment.
     */
    override val currentFilter: FeedFilter
        get() = adapter.feed.filter

    override val title: TitleFragment.Title?
        get() {
            val titleOverride = arguments?.getString(ARG_TITLE)

            if (settings.useTopTagAsTitle) {
                val title = latestActivePostFragment?.title

                if (titleOverride != null) {
                    // if the caller gave us a more specific title, we'll use that one.
                    return title?.copy(subTitle = titleOverride)
                            ?: TitleFragment.Title(titleOverride)
                }

                return title
            } else {
                val context = this.context ?: return null
                return FeedFilterFormatter.toTitle(context, currentFilter)
            }
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

    private fun saveStateToBundle(outState: Bundle) {
        if (view != null && adapter.feed.isNotEmpty()) {
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

    private inner class PostAdapter(
            feed: Feed,
            private val manager: FeedManager)
        : FragmentStateAdapter(this) {

        var feed: Feed by observeChangeEx(feed) { oldValue, newValue ->
            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = oldValue.size
                override fun getNewListSize(): Int = newValue.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return oldValue[oldItemPosition].id == newValue[newItemPosition].id
                }

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return oldValue[oldItemPosition] == newValue[newItemPosition]
                }
            })

            diff.dispatchUpdatesTo(this)
        }

        @Suppress("UNCHECKED_CAST")
        fun cleanupSavedStates() {
            if (cleanupOfSavedStateFailed) {
                logger.debug { "Skip cleanup as it failed earlier." }
                return
            }

            logger.time("Cleanup savedStates using reflection") {
                try {
                    val mFragments = FragmentStateAdapter::class.java.getDeclaredField("mFragments").let { field ->
                        field.isAccessible = true
                        field.get(this) as LongSparseArray<Fragment>
                    }

                    val mSavedStates = FragmentStateAdapter::class.java.getDeclaredField("mSavedStates").let { field ->
                        field.isAccessible = true
                        field.get(this) as LongSparseArray<Fragment.SavedState>
                    }

                    val keysToRemove = mutableListOf<Long>()
                    for (idx in 0 until mSavedStates.size()) {
                        val key = mSavedStates.keyAt(idx)
                        if (!mFragments.containsKey(key)) {
                            keysToRemove += key
                        }
                    }

                    logger.info { "Removing keys from savedState: $keysToRemove" }
                    keysToRemove.forEach { key -> mSavedStates.remove(key) }

                } catch (err: Exception) {
                    cleanupOfSavedStateFailed = true
                    AndroidUtility.logToCrashlytics(err)

                    // forward when debugging
                    debug { throw err }
                }
            }
        }

        override fun getItemCount(): Int {
            return feed.size
        }

        override fun createFragment(position: Int): Fragment {
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

        override fun getItemId(position: Int): Long {
            return feed[position].id
        }

        override fun containsItem(itemId: Long): Boolean {
            return feed.any { it.id == itemId }
        }
    }

    companion object {
        private var cleanupOfSavedStateFailed = false

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
