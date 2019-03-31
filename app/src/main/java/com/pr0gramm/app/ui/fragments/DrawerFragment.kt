package com.pr0gramm.app.ui.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import androidx.core.view.postDelayed
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.RequestCodes
import com.pr0gramm.app.UserClassesService
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.model.bookmark.Bookmark
import com.pr0gramm.app.services.*
import com.pr0gramm.app.services.NavigationProvider.NavigationItem
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.ui.*
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindView
import com.pr0gramm.app.ui.dialogs.LogoutDialogFragment
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.AndroidUtility.getStatusBarHeight
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.di.instance
import com.squareup.picasso.Picasso
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit

/**
 */
class DrawerFragment : BaseFragment("DrawerFragment") {
    private val userService: UserService by instance()
    private val bookmarkService: BookmarkService by instance()
    private val userClassesService: UserClassesService by instance()

    private val currentSelection = BehaviorSubject.create(null as FeedFilter?)

    private val navigationProvider: NavigationProvider by lazy {
        val k = requireContext().injector
        val picasso = k.instance<Picasso>()
        val inboxService = k.instance<InboxService>()
        val configService = k.instance<ConfigService>()
        val singleShotService = k.instance<SingleShotService>()
        NavigationProvider(requireActivity(), userService, inboxService,
                bookmarkService, configService, singleShotService, picasso)
    }

    private val navItemsRecyclerView: RecyclerView by bindView(R.id.drawer_nav_list)

    private lateinit var navigationAdapter: Adapter

    private val doIfAuthorizedHelper = LoginActivity.helper(this)

    private lateinit var defaultColor: ColorStateList
    private lateinit var markedColor: ColorStateList


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.left_drawer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.context.obtainStyledAttributes(intArrayOf(R.attr.colorAccent, android.R.attr.textColorPrimary)).use { result ->
            markedColor = ColorStateList.valueOf(result.getColor(result.getIndex(0), 0))
            defaultColor = ColorStateList.valueOf(result.getColor(result.getIndex(1), 0))
        }

        navigationAdapter = Adapter(callback)

        // initialize the top navigation items
        navItemsRecyclerView.adapter = navigationAdapter
        navItemsRecyclerView.itemAnimator = null
        navItemsRecyclerView.layoutManager = LinearLayoutManager(activity)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        doIfAuthorizedHelper.onActivityResult(requestCode, resultCode)
    }

    override suspend fun onResumeImpl() {

        val rxNavItems = navigationProvider.navigationItems(currentSelection)
                .subscribeOn(Schedulers.computation())
                .startWith(listOf<NavigationItem>())

        rx.Observable
                .combineLatest(userService.loginStateWithBenisGraph, rxNavItems) { state, navItems ->
                    mutableListOf<Any>().also { items ->
                        items += TitleInfo(state.loginState.name, state.loginState.mark)

                        if (state.loginState.authorized) {
                            items += BenisInfo(state.loginState.score, state.benisGraph)
                        }

                        items += Spacer
                        items.addAll(navItems)
                        items += Spacer
                    }
                }

                .distinctUntilChanged()

                // the first should come directly and can be applied here in "resume"
                .debounce(100, TimeUnit.MILLISECONDS, MainThreadScheduler)
                .bindToLifecycle()
                .subscribe { items ->
                    logger.debug { "Submitting ${items.size} navigation items" }
                    navigationAdapter.submitList(items)
                }
    }

    fun updateCurrentFilters(current: FeedFilter?) {
        currentSelection.onNext(current)
    }

    interface Callbacks {
        /**
         * Called if a drawer filter was clicked.
         * @param filter The feed filter that was clicked.
         */
        fun onFeedFilterSelectedInNavigation(filter: FeedFilter, startAt: CommentRef? = null)

        /*
         * Called if the user name itself was clicked.
         */
        fun onUsernameClicked()

        /**
         * Some other menu item was clicked and we request that this
         * drawer gets closed
         */
        fun onOtherNavigationItemClicked()

        /**
         * Navigate to the favorites of the given user
         */
        fun onNavigateToFavorites(username: String)

        fun hintBookmarksEditableWithPremium()
    }

    private val callback: Callbacks
        get() {
            return activity as Callbacks
        }

    private inner class Adapter(callbacks: Callbacks) : DelegateAdapter<Any>() {
        init {
            delegates += NavigationDelegateAdapter(R.layout.left_drawer_nav_item)
            delegates += NavigationDelegateAdapter(R.layout.left_drawer_nav_item_hint)
            delegates += NavigationDelegateAdapter(R.layout.left_drawer_nav_item_inbox)
            delegates += NavigationDelegateAdapter(R.layout.left_drawer_nav_item_divider)
            delegates += NavigationDelegateAdapter(R.layout.left_drawer_nav_item_special)

            delegates += TitleDelegateAdapter(callbacks, userClassesService)
            delegates += BenisGraphDelegateAdapter(callbacks)

            delegates += SpacerAdapterDelegate
        }
    }

    private inner class NavigationDelegateAdapter(@LayoutRes private val layoutId: Int)
        : ItemAdapterDelegate<NavigationItem, Any, NavigationItemViewHolder>() {

        override fun isForViewType(value: Any): Boolean {
            return value is NavigationItem && value.layout == layoutId
        }

        override fun onCreateViewHolder(parent: ViewGroup): NavigationItemViewHolder {
            val inflater = LayoutInflater.from(parent.context).cloneInContext(parent.context)
            val view = inflater.inflate(layoutId, parent, false)
            return NavigationItemViewHolder(view)
        }

        override fun onBindViewHolder(holder: NavigationItemViewHolder, value: NavigationItem) {
            if (holder.text != null) {
                holder.text.text = value.title ?: ""

                // set the icon of the image
                holder.text.setCompoundDrawablesWithIntrinsicBounds(value.icon, null, null, null)

                // update color
                if (value.action !== NavigationProvider.ActionType.HINT) {
                    val textColor: ColorStateList
                    val iconColor: ColorStateList

                    if (value.colorOverride != null) {
                        textColor = defaultColor
                        iconColor = ColorStateList.valueOf(value.colorOverride)
                    } else {
                        textColor = if (value.isSelected) markedColor else defaultColor
                        iconColor = textColor.withAlpha(127)
                    }

                    holder.text.setTextColor(textColor)
                    changeCompoundDrawableColor(holder.text, iconColor)
                }
            }

            // handle clicks
            holder.itemView.setOnClickListener {
                dispatchItemClick(value)
            }

            holder.itemView.setOnLongClickListener {
                if (value.bookmark != null) {
                    if (bookmarkService.canEdit) {
                        showDialogToRemoveBookmark(value.bookmark)
                    } else {
                        callback.hintBookmarksEditableWithPremium()
                    }
                }

                true
            }

            holder.action?.setOnClickListener {
                value.customAction()
            }

            if (value.action === NavigationProvider.ActionType.MESSAGES) {
                holder.unread?.apply {
                    text = value.unreadCount.total.toString()
                    visible = value.unreadCount.total > 0
                }
            }
        }
    }

    private fun dispatchItemClick(item: NavigationItem) {
        when (item.action) {
            NavigationProvider.ActionType.HINT,
            NavigationProvider.ActionType.DIVIDER ->
                Unit

            NavigationProvider.ActionType.FILTER,
            NavigationProvider.ActionType.BOOKMARK ->
                callback.onFeedFilterSelectedInNavigation(item.filter!!)

            NavigationProvider.ActionType.UPLOAD -> {
                showUploadActivity()
                callback.onOtherNavigationItemClicked()
            }

            NavigationProvider.ActionType.MESSAGES -> {
                showInboxActivity(item.unreadCount)
                callback.onOtherNavigationItemClicked()
            }

            NavigationProvider.ActionType.FAVORITES ->
                item.filter?.likes?.let { likes ->
                    callback.onNavigateToFavorites(likes)
                }

            NavigationProvider.ActionType.URI ->
                item.uri?.let { openActionUri(it) }

            NavigationProvider.ActionType.SETTINGS ->
                startActivity<SettingsActivity>()

            NavigationProvider.ActionType.INVITES ->
                startActivity<InviteActivity>()

            NavigationProvider.ActionType.CONTACT ->
                activity?.startActivity<ContactActivity>(RequestCodes.FEEDBACK)

            NavigationProvider.ActionType.FAQ -> {
                Track.registerFAQClicked()
                BrowserHelper.openCustomTab(requireActivity(),
                        Uri.parse("https://pr0gramm.com/faq:all?iap=true"),
                        handover = false)
            }

            NavigationProvider.ActionType.PREMIUM -> {
                Track.registerLinkClicked()
                val uri = Uri.parse("https://pr0gramm.com/pr0mium/iap?iap=true")
                BrowserHelper.openCustomTab(requireActivity(), uri)
            }

            NavigationProvider.ActionType.LOGIN ->
                startActivity<LoginActivity>()

            NavigationProvider.ActionType.LOGOUT ->
                LogoutDialogFragment().show(fragmentManager, null)

        }
    }

    private fun openActionUri(uri: Uri) {
        Track.specialMenuActionClicked(uri)

        // check if we can handle the uri in the app
        FilterParser.parse(uri)?.let { parsed ->
            callback.onFeedFilterSelectedInNavigation(parsed.filter, parsed.start)
            return
        }

        BrowserHelper.openCustomTab(context ?: return, uri)
    }

    private fun showInboxActivity(unreadCounts: Api.InboxCounts) {
        if (unreadCounts.comments > 0 || unreadCounts.mentions > 0) {
            showInboxActivity(InboxType.COMMENTS_IN)
        } else {
            showInboxActivity(InboxType.PRIVATE)
        }
    }

    private fun showInboxActivity(inboxType: InboxType) {
        val run = Runnable {
            val intent = Intent(activity, InboxActivity::class.java)
            intent.putExtra(InboxActivity.EXTRA_INBOX_TYPE, inboxType.ordinal)
            startActivity(intent)
        }

        doIfAuthorizedHelper.run(run, run)
    }

    private fun showUploadActivity() {
        val run = Runnable {
            (activity as MainActionHandler).showUploadBottomSheet()
        }

        doIfAuthorizedHelper.run(run, run)
    }

    private fun showDialogToRemoveBookmark(bookmark: Bookmark) {
        showBottomSheet(this) {
            content(R.string.do_you_want_to_remove_this_bookmark)
            negative(R.string.cancel)
            positive(R.string.delete) { bookmarkService.delete(bookmark) }
        }
    }

    /**
     * Fakes the drawable tint by applying a color filter on all compound
     * drawables of this view.

     * @param view  The view to "tint"
     * @param color The color with which the drawables are to be tinted.
     */
    private fun changeCompoundDrawableColor(view: TextView, color: ColorStateList) {
        val defaultColor = color.defaultColor
        val drawables = view.compoundDrawables

        drawables.filterNotNull().forEach {
            // fake the tint with a color filter.
            it.mutate().setColorFilter(defaultColor, PorterDuff.Mode.SRC_IN)
        }
    }

    fun scrollTo(filter: FeedFilter) {
        navItemsRecyclerView.postDelayed(delayInMillis = 500) {
            val context = requireContext()

            val idx = navigationAdapter.items.indexOfFirst { item -> item is NavigationItem && item.filter == filter }
            if (idx == -1)
                return@postDelayed

            val lm = navItemsRecyclerView.layoutManager as? LinearLayoutManager
                    ?: return@postDelayed

            lm.startSmoothScroll(OverscrollLinearSmoothScroller(context, idx,
                    dontScrollIfVisible = true,
                    offsetTop = AndroidUtility.getActionBarContentOffset(context) + context.dip2px(32),
                    offsetBottom = context.dip2px(32)))
        }
    }

    private class NavigationItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView? = (itemView as? TextView ?: itemView.findOptional(R.id.title))
        val unread: TextView? = itemView.findOptional(R.id.unread_count)
        val action: Button? = itemView.findOptional(R.id.action_okay)
    }
}


data class TitleInfo(val name: String?, val mark: Int = 0)

private class TitleDelegateAdapter(private val callbacks: DrawerFragment.Callbacks, private val userClassesService: UserClassesService)
    : ListItemTypeAdapterDelegate<TitleInfo, TitleViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): TitleViewHolder {
        return TitleViewHolder(parent.inflateDetachedChild(R.layout.left_drawer_nav_title))
    }

    override fun onBindViewHolder(holder: TitleViewHolder, value: TitleInfo) {
        if (value.name == null) {
            holder.title.setText(R.string.pr0gramm)
            holder.title.setOnClickListener(null)

            holder.subtitle.visible = false
        } else {
            holder.title.text = value.name
            holder.title.setOnClickListener { callbacks.onUsernameClicked() }

            val userClass = userClassesService.get(value.mark)
            holder.subtitle.visible = true
            holder.subtitle.text = userClass.name
            holder.subtitle.setTextColor(userClass.color)
            holder.subtitle.setOnClickListener { callbacks.onUsernameClicked() }
        }
    }
}

private class TitleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val title = find<TextView>(R.id.title)
    val subtitle = find<TextView>(R.id.subtitle)

    init {
        // add some space on the top for the translucent status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            itemView.updatePaddingRelative(top = getStatusBarHeight(itemView.context))
        }
    }
}

data class BenisInfo(val score: Int, val graph: Graph?)

private class BenisGraphDelegateAdapter(private val callbacks: DrawerFragment.Callbacks)
    : ListItemTypeAdapterDelegate<BenisInfo, BenisGraphViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): BenisGraphViewHolder {
        return BenisGraphViewHolder(parent.inflateDetachedChild(R.layout.left_drawer_nav_benis))
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: BenisGraphViewHolder, value: BenisInfo) {
        val context = holder.itemView.context

        holder.benis.text = context.getString(R.string.benis, value.score)

        val graph = value.graph
        if (graph != null && graph.points.size >= 2) {
            holder.graph.setImageDrawable(GraphDrawable(graph))
            holder.graph.visible = true

            val delta = (graph.last.y - graph.first.y).toInt()
            val color = if (delta < 0) R.color.benis_delta_negative else R.color.benis_delta_positive
            holder.delta.setTextColor(ContextCompat.getColor(context, color))
            holder.delta.text = (if (delta < 0) "↓" else "↑") + delta
            holder.delta.visible = true
        } else {
            holder.graph.setImageDrawable(null)
            holder.graph.visible = false

            holder.delta.visible = false
        }

        holder.itemView.setOnClickListener {
            context.startActivity<StatisticsActivity>()
            callbacks.onOtherNavigationItemClicked()
        }
    }
}


private class BenisGraphViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val benis = find<TextView>(R.id.benis)
    val delta = find<TextView>(R.id.benis_delta)
    val graph = find<ImageView>(R.id.benis_graph)
}


private object Spacer

private object SpacerAdapterDelegate : ListItemTypeAdapterDelegate<Spacer, RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        return object : RecyclerView.ViewHolder(
                parent.inflateDetachedChild(R.layout.left_drawer_nav_spacer)) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, value: Spacer) {
    }
}

