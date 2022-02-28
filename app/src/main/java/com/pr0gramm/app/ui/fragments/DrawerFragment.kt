package com.pr0gramm.app.ui.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
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
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.core.view.updatePaddingRelative
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.RequestCodes
import com.pr0gramm.app.UserClassesService
import com.pr0gramm.app.databinding.LeftDrawerBinding
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.orm.isImmutable
import com.pr0gramm.app.services.*
import com.pr0gramm.app.services.NavigationProvider.NavigationItem
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.time
import com.pr0gramm.app.ui.*
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindViews
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.dialogs.EditBookmarkDialog
import com.pr0gramm.app.ui.dialogs.LogoutDialogFragment
import com.pr0gramm.app.ui.upload.UploadTypeDialogFragment
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.AndroidUtility.getStatusBarHeight
import com.pr0gramm.app.util.di.Injector
import com.pr0gramm.app.util.di.InjectorAware
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.di.instance
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

/**
 *
 */
@OptIn(ExperimentalStdlibApi::class)
class DrawerFragment : BaseFragment("DrawerFragment", R.layout.left_drawer) {
    private val userService: UserService by instance()
    private val bookmarkService: BookmarkService by instance()
    private val userClassesService: UserClassesService by instance()

    private val currentSelection = MutableStateFlow(null as FeedFilter?)

    private val views by bindViews(LeftDrawerBinding::bind)

    private lateinit var navigationAdapter: Adapter

    private val doIfAuthorizedHelper = LoginActivity.helper(this)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        navigationAdapter = Adapter(requireActivity() as Callbacks)

        // initialize the top navigation items
        views.drawerNavList.adapter = navigationAdapter
        views.drawerNavList.itemAnimator = null
        views.drawerNavList.layoutManager = LinearLayoutManager(activity)

        observeNavigationItems()
    }

    private fun observeNavigationItems() {
        val provider = with(requireContext().injector) {
            val picasso = instance<Picasso>()
            val inboxService = instance<InboxService>()
            val configService = instance<ConfigService>()
            val singleShotService = instance<SingleShotService>()

            logger.time("Create navigation provider") {
                NavigationProvider(requireActivity(), userService, inboxService,
                        bookmarkService, configService, singleShotService, picasso)
            }
        }

        val rxNavItems = provider.navigationItems(currentSelection)
                .flowOn(Dispatchers.Default)
                .onStart { emit(listOf()) }

        val elements = combine(userService.loginStateWithBenisGraph, rxNavItems, userClassesService.onChange) { state, navItems, _ ->
            buildList {
                val userClass = userClassesService.get(state.loginState.mark)
                add(TitleInfo(state.loginState.name, userClass))

                if (state.loginState.authorized) {
                    add(BenisInfo(state.loginState.score, state.scoreGraph))
                }

                add(Spacer)
                addAll(navItems)
                add(Spacer)
            }
        }

        launchWhenStarted {
            elements.distinctUntilChanged().debounce(100).collect { items ->
                logger.debug { "Submitting ${items.size} navigation items" }
                navigationAdapter.submitList(items)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        doIfAuthorizedHelper.onActivityResult(requestCode, resultCode)
    }

    fun updateCurrentFilters(current: FeedFilter?) {
        currentSelection.value = current
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
        fun onNavigateToCollections(username: String)

        fun hintBookmarksEditableWithPremium()
    }

    private inner class Adapter(callbacks: Callbacks) : DelegateAdapter<Any>() {
        init {
            val viewContext = requireView().context

            delegates += NavigationDelegateAdapter(viewContext, requireActivity(), doIfAuthorizedHelper, callbacks, R.layout.left_drawer_nav_item)
            delegates += NavigationDelegateAdapter(viewContext, requireActivity(), doIfAuthorizedHelper, callbacks, R.layout.left_drawer_nav_item_hint)
            delegates += NavigationDelegateAdapter(viewContext, requireActivity(), doIfAuthorizedHelper, callbacks, R.layout.left_drawer_nav_item_inbox)
            delegates += NavigationDelegateAdapter(viewContext, requireActivity(), doIfAuthorizedHelper, callbacks, R.layout.left_drawer_nav_item_trending)
            delegates += NavigationDelegateAdapter(viewContext, requireActivity(), doIfAuthorizedHelper, callbacks, R.layout.left_drawer_nav_item_divider)
            delegates += NavigationDelegateAdapter(viewContext, requireActivity(), doIfAuthorizedHelper, callbacks, R.layout.left_drawer_nav_item_special)

            delegates += TitleDelegateAdapter(callbacks)
            delegates += BenisGraphDelegateAdapter(callbacks)

            delegates += SpacerAdapterDelegate
        }
    }

    fun scrollTo(filter: FeedFilter) {
        views.drawerNavList.postDelayed(delayInMillis = 500) {
            val context = requireContext()

            val idx = navigationAdapter.items.indexOfFirst { item -> item is NavigationItem && item.filter == filter }
            if (idx == -1)
                return@postDelayed

            val lm = views.drawerNavList.layoutManager as? LinearLayoutManager
                    ?: return@postDelayed

            lm.startSmoothScroll(OverscrollLinearSmoothScroller(context, idx,
                    dontScrollIfVisible = true,
                    offsetTop = AndroidUtility.getActionBarContentOffset(context) + context.dp(32),
                    offsetBottom = context.dp(32)))
        }
    }
}

private class NavigationItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val action: View = itemView.findViewWithTag("main-action") ?: itemView

    val text: TextView? = (itemView as? TextView ?: itemView.findOptional(R.id.title))
    val unread: TextView? = itemView.findOptional(R.id.unread_count)
    val actionCustom: Button? = itemView.findOptional(R.id.action_custom)
    val actionSecondary: View? = itemView.findOptional(R.id.action_secondary)
}

private class NavigationDelegateAdapter(
        viewContext: Context,
        private val activity: FragmentActivity,
        private val doIfAuthorizedHelper: LoginActivity.DoIfAuthorizedHelper,
        private val callback: DrawerFragment.Callbacks,
        @LayoutRes private val layoutId: Int)

    : ItemAdapterDelegate<NavigationItem, Any, NavigationItemViewHolder>(), InjectorAware {

    override val injector: Injector = activity.injector

    private val bookmarkService: BookmarkService = instance()

    private val defaultColor: ColorStateList
    private val markedColor: ColorStateList

    init {
        viewContext.obtainStyledAttributes(intArrayOf(R.attr.colorAccent, android.R.attr.textColorPrimary)).use { result ->
            markedColor = ColorStateList.valueOf(result.getColor(result.getIndex(0), 0))
            defaultColor = ColorStateList.valueOf(result.getColor(result.getIndex(1), 0))
        }
    }

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
        }

        // update color
        if (value.action !== NavigationProvider.ActionType.HINT) {
            val textColor: ColorStateList
            val iconColor: ColorStateList

            if (value.colorOverride != null) {
                textColor = defaultColor
                iconColor = ColorStateList.valueOf(value.colorOverride)
            } else {
                textColor = if (value.isSelected) markedColor else defaultColor
                iconColor = if (value.isSelected) markedColor else defaultColor.withAlpha(128)
            }

            if (holder.text != null) {
                holder.text.setTextColor(textColor)
                changeCompoundDrawableColor(holder.text, iconColor)
                holder.text.isSelected = value.isSelected
            }

            if (holder.actionSecondary is ImageView) {
                holder.actionSecondary.imageTintList = iconColor
            }
        }


        // handle clicks
        holder.action.setOnClickListener {
            dispatchItemAction(value)
        }

        holder.actionSecondary?.setOnClickListener {
            dispatchItemSecondaryAction(value)
        }

        holder.action.setOnLongClickListener {
            if (value.bookmark != null && !value.bookmark.isImmutable) {
                if (bookmarkService.canEdit) {
                    EditBookmarkDialog.forBookmark(value.bookmark).show(activity.supportFragmentManager, null)
                } else {
                    callback.hintBookmarksEditableWithPremium()
                }
            }

            true
        }

        holder.actionCustom?.setOnClickListener {
            value.customAction()
        }

        if (value.action === NavigationProvider.ActionType.MESSAGES) {
            holder.unread?.apply {
                text = value.unreadCount.total.toString()
                isVisible = value.unreadCount.total > 0
            }
        }
    }

    private fun dispatchItemAction(item: NavigationItem) {
        when (item.action) {
            NavigationProvider.ActionType.HINT,
            NavigationProvider.ActionType.DIVIDER ->
                Unit

            NavigationProvider.ActionType.FILTER,
            NavigationProvider.ActionType.BOOKMARK -> {
                val filter = item.filter ?: return debugCrash(Unit)
                callback.onFeedFilterSelectedInNavigation(filter)
            }

            NavigationProvider.ActionType.UPLOAD -> {
                showUploadActivity()
                callback.onOtherNavigationItemClicked()
            }

            NavigationProvider.ActionType.MESSAGES -> {
                showInboxActivity()
                callback.onOtherNavigationItemClicked()
            }

            NavigationProvider.ActionType.COLLECTIONS -> {
                val username = item.filter?.username ?: return debugCrash()
                callback.onNavigateToCollections(username)
            }

            NavigationProvider.ActionType.URI ->
                item.uri?.let { openActionUri(it) }

            NavigationProvider.ActionType.SETTINGS ->
                activity.startActivity<SettingsActivity>()

            NavigationProvider.ActionType.INVITES ->
                activity.startActivity<InviteActivity>()

            NavigationProvider.ActionType.CONTACT ->
                activity.startActivity<ContactActivity>(RequestCodes.FEEDBACK)

            NavigationProvider.ActionType.FAQ -> {
                Track.registerFAQClicked()
                BrowserHelper.openCustomTab(activity,
                        Uri.parse("https://pr0gramm.com/faq:all?iap=true"),
                        handover = false)
            }

            NavigationProvider.ActionType.PREMIUM -> {
                Track.registerLinkClicked()
                val uri = Uri.parse("https://pr0gramm.com/pr0mium/iap?iap=true")
                BrowserHelper.openCustomTab(activity, uri)
            }

            NavigationProvider.ActionType.AGE_VERIFICATION -> {
                val uri = Uri.parse("https://pr0gramm.com/verify")
                BrowserHelper.openCustomTab(activity, uri, handover = true)
            }


            NavigationProvider.ActionType.LOGIN ->
                activity.startActivity<LoginActivity>()

            NavigationProvider.ActionType.LOGOUT ->
                LogoutDialogFragment().show(activity.supportFragmentManager, null)

        }
    }

    private fun dispatchItemSecondaryAction(item: NavigationItem) {
        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (item.action) {
            NavigationProvider.ActionType.BOOKMARK -> {
                val filter = item.filterInverse ?: return debugCrash()
                callback.onFeedFilterSelectedInNavigation(filter)
            }
        }
    }

    private fun openActionUri(uri: Uri) {
        Track.specialMenuActionClicked(uri)

        // check if we can handle the uri in the app
        FilterParser.parse(uri)?.let { parsed ->
            callback.onFeedFilterSelectedInNavigation(parsed.filter, parsed.start)
            return
        }

        BrowserHelper.openCustomTab(activity, uri)
    }

    private fun showInboxActivity() {
        showInboxActivity(InboxType.PRIVATE)
    }

    private fun showInboxActivity(inboxType: InboxType) {
        doIfAuthorizedHelper.runAuthWithRetry {
            val intent = Intent(activity, InboxActivity::class.java)
            intent.putExtra(InboxActivity.EXTRA_INBOX_TYPE, inboxType.ordinal)
            activity.startActivity(intent)
        }
    }

    private fun showUploadActivity() {
        doIfAuthorizedHelper.runAuthWithRetry {
            val dialog = UploadTypeDialogFragment()
            (activity as MainActionHandler).showUploadBottomSheet(dialog)
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

        drawables.filterNotNull().forEach { drawable ->
            // fake the tint with a color filter.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                drawable.mutate().colorFilter = BlendModeColorFilter(defaultColor, BlendMode.SRC_IN)
            } else {
                drawable.mutate().setColorFilter(defaultColor, PorterDuff.Mode.SRC_IN)
            }
        }
    }
}


data class TitleInfo(val name: String?, val userClass: UserClassesService.UserClass)

private class TitleDelegateAdapter(private val callbacks: DrawerFragment.Callbacks)
    : ListItemTypeAdapterDelegate<TitleInfo, Any, TitleViewHolder>(TitleInfo::class) {

    override fun onCreateViewHolder(parent: ViewGroup): TitleViewHolder {
        return TitleViewHolder(parent.inflateDetachedChild(R.layout.left_drawer_nav_title))
    }

    override fun onBindViewHolder(holder: TitleViewHolder, value: TitleInfo) {
        if (value.name == null) {
            holder.title.setText(R.string.pr0gramm)
            holder.title.setOnClickListener(null)

            holder.subtitle.isVisible = false
        } else {
            holder.title.text = value.name
            holder.title.setOnClickListener { callbacks.onUsernameClicked() }

            holder.subtitle.isVisible = true
            holder.subtitle.text = value.userClass.name
            holder.subtitle.setTextColor(value.userClass.color)
            holder.subtitle.setOnClickListener { callbacks.onUsernameClicked() }
        }
    }
}

private class TitleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val title = find<TextView>(R.id.title)
    val subtitle = find<TextView>(R.id.subtitle)

    init {
        itemView.updatePaddingRelative(top = getStatusBarHeight(itemView.context))
    }
}

data class BenisInfo(val score: Int, val graph: Graph?)

private class BenisGraphDelegateAdapter(private val callbacks: DrawerFragment.Callbacks)
    : ListItemTypeAdapterDelegate<BenisInfo, Any, BenisGraphViewHolder>(BenisInfo::class) {

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
            holder.graph.isVisible = true

            val delta = (graph.last.y - graph.first.y).toInt()
            val color = if (delta < 0) R.color.benis_delta_negative else R.color.benis_delta_positive
            holder.delta.setTextColor(context.getColorCompat(color))
            holder.delta.text = (if (delta < 0) "↓" else "↑") + delta
            holder.delta.isVisible = true
        } else {
            holder.graph.setImageDrawable(null)
            holder.graph.isVisible = false

            holder.delta.isVisible = false
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

private object SpacerAdapterDelegate : ListItemTypeAdapterDelegate<Spacer, Any, RecyclerView.ViewHolder>(Spacer::class) {
    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        return object : RecyclerView.ViewHolder(
                parent.inflateDetachedChild(R.layout.left_drawer_nav_spacer)) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, value: Spacer) {
    }
}

