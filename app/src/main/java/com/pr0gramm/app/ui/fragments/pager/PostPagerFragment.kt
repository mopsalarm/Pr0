package com.pr0gramm.app.ui.fragments.pager

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.viewpager.widget.ViewPager
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.databinding.FragmentPostPagerBinding
import com.pr0gramm.app.feed.Feed
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.parcel.getParcelableOrNull
import com.pr0gramm.app.parcel.getParcelableOrThrow
import com.pr0gramm.app.ui.FeedFilterFormatter
import com.pr0gramm.app.ui.FilterFragment
import com.pr0gramm.app.ui.MainActionHandler
import com.pr0gramm.app.ui.MainActivity
import com.pr0gramm.app.ui.PreviewInfo
import com.pr0gramm.app.ui.ScrollHideToolbarListener.ToolbarActivity
import com.pr0gramm.app.ui.TitleFragment
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindViews
import com.pr0gramm.app.ui.base.launchInViewScope
import com.pr0gramm.app.ui.fragments.CommentRef
import com.pr0gramm.app.ui.fragments.IdFragmentStatePagerAdapter
import com.pr0gramm.app.ui.fragments.PreviewInfoSource
import com.pr0gramm.app.ui.fragments.feed.FeedFragment
import com.pr0gramm.app.ui.fragments.post.PostFragment
import com.pr0gramm.app.ui.viewModels
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.observeChangeEx

/**
 */
class PostPagerFragment : BaseFragment("PostPagerFragment", R.layout.fragment_post_pager), FilterFragment, TitleFragment, PreviewInfoSource {

    private val views by bindViews(FragmentPostPagerBinding::bind)

    private val model by viewModels { savedState ->
        // get the feed to show and setup a loader to load more data
        val feed = requireArguments().getParcelableOrThrow<Feed.FeedParcel>(ARG_FEED).feed
        val startItem = requireArguments().getParcelableOrThrow<FeedItem>(ARG_START_ITEM)

        PostPagerViewModel(
                savedState = PostPagerViewModel.SavedState(savedState, feed, startItem),
                feedService = instance(),
        )
    }

    private lateinit var adapter: PostAdapter

    private var previewInfo: PreviewInfo? = null

    private var latestActivePostFragment: PostFragment? = null

    private var initialCommentRef: CommentRef? = null

    private val fcb = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            if (f is PostFragment && latestActivePostFragment !== f) {
                latestActivePostFragment = f
                updateTitle()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initialCommentRef = requireArguments().getParcelableOrNull(ARG_START_ITEM_COMMENT_REF)

        // Listen for changes in fragments. We use this, because with the ViewPager2
        // callbacks we don't have any access to the fragment, and we don't even know,
        // when the fragment will be there and available.
        childFragmentManager.registerFragmentLifecycleCallbacks(fcb, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // create the adapter on the view
        adapter = PostAdapter()

        launchInViewScope {
            model.state.collect { state ->
                logger.debug { "Update state: $state" }
                adapter.feed = state.feed
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
                    activateFragmentAt(position)
                }

                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            })
        }

        if (Settings.fancyScrollHorizontal) {
            views.pager.setPageTransformer(false) { page: View, position: Float ->
                val viewer = page.findViewWithTag<View>(PostFragment.ViewerTag)
                if (viewer != null) {
                    viewer.translationX = -(position * page.width / 5.0f)
                }
            }
        }

        views.pager.adapter = adapter
        views.pager.offscreenPageLimit = 1

        // calculate index of the first item to show if this is the first
        // time we show this fragment.
        makeItemCurrent(model.currentItem)
    }

    private fun activateFragmentAt(position: Int) {
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

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        // if we've restored only a subset of the items, the index will be out of range.
        // we now need to restore it based on the item id.
        if (adapter.feed.getOrNull(views.pager.currentItem)?.id != model.currentItem.id) {
            logger.debug { "Going to restore view position in onViewStateRestored" }
            makeItemCurrent(model.currentItem)
        }
    }

    private fun updateTitle() {
        val activity = activity as? MainActivity ?: return
        activity.updateActionbarTitle()
    }

    override fun onStop() {
        val target = targetFragment as? FeedFragment

        // merge the updated feed back into our parent fragment.
        val feed = model.state.value.feed
        if (target != null && model.currentItem.id in feed) {
            target.updateFeedItemTarget(feed, model.currentItem)
        }

        super.onStop()
    }

    private fun makeItemCurrent(item: FeedItem) {
        val index = adapter.feed.indexById(item.id) ?: 0

        if (views.pager.currentItem != index) {
            logger.info { "Move pager to item ${item.id} at index: $index" }
            views.pager.setCurrentItem(index, false)
        }
    }

    override fun previewInfoFor(item: FeedItem): PreviewInfo? {
        return previewInfo?.takeIf { it.itemId == item.id }
    }

    /**
     * Returns the feed filter for this fragment.
     */
    override val currentFilter: FeedFilter
        get() = model.state.value.feed.filter

    override val title: TitleFragment.Title?
        get() = buildFragmentTitle()

    private fun buildFragmentTitle(): TitleFragment.Title? {
        val titleOverride = requireArguments().getString(ARG_TITLE)

        if (Settings.useTopTagAsTitle) {
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
        handler.onFeedFilterSelected(currentFilter.basicWithTags(tag.text))
    }

    fun onUsernameClicked(username: String) {
        // always show all uploads of a user.
        val newFilter = currentFilter
            .withFeedType(FeedType.NEW)
            .basicWithUser(username)

        (activity as MainActionHandler).onFeedFilterSelected(newFilter)
    }

    /**
     * Sets the pixels that should be used in the transition.
     */
    fun setPreviewInfo(previewInfo: PreviewInfo) {
        this.previewInfo = previewInfo
    }

    private inner class PostAdapter
        : IdFragmentStatePagerAdapter(childFragmentManager) {

        var feed: Feed by observeChangeEx(Feed()) { oldValue, newValue ->
            if (oldValue != newValue) {
                notifyDataSetChanged()
            }
        }

        override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
            super.setPrimaryItem(container, position, `object`)

            val currentItemIndex = views.pager.currentItem
            val currentItem = feed.getOrNull(currentItemIndex)
            logger.debug { "setPrimaryItem($currentItemIndex, ${currentItem?.id})" }
            if (currentItem != null) {
                // item was updated, make it the new current one.
                model.currentItem = currentItem
            }
        }

        override fun getItem(position: Int): Fragment {
            when {
                position > feed.size - 12 -> {
                    logger.debug { "Requested pos=$position, load next page" }
                    model.triggerLoadNext()
                }

                position < 12 -> {
                    logger.debug { "Requested pos=$position, load prev page" }
                    model.triggerLoadPrev()
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
