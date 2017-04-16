package com.pr0gramm.app.ui.fragments

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.github.salomonbrys.kodein.instance
import com.github.salomonbrys.kodein.with
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.R
import com.pr0gramm.app.UserClasses
import com.pr0gramm.app.api.pr0gramm.LoginCookieHandler
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.orm.Bookmark
import com.pr0gramm.app.services.*
import com.pr0gramm.app.services.NavigationProvider.NavigationItem
import com.pr0gramm.app.ui.*
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment
import com.pr0gramm.app.ui.dialogs.LogoutDialogFragment
import com.pr0gramm.app.util.AndroidUtility.getStatusBarHeight
import com.pr0gramm.app.util.CustomTabsHelper
import com.pr0gramm.app.util.onErrorResumeEmpty
import com.pr0gramm.app.util.use
import com.pr0gramm.app.util.visible
import kotterknife.bindView
import rx.functions.Action1
import java.util.*

/**
 */
class DrawerFragment : BaseFragment() {
    private val userService: UserService by instance()
    private val cookieHandler: LoginCookieHandler by instance()
    private val bookmarkService: BookmarkService by instance()

    private val navigationProvider: NavigationProvider by injector.with { activity }.instance()

    private val usernameView: TextView by bindView(R.id.username)
    private val userTypeView: TextView by bindView(R.id.user_type)
    private val benisView: TextView by bindView(R.id.kpi_benis)
    private val benisDeltaView: TextView by bindView(R.id.benis_delta)
    private val benisContainer: View by bindView(R.id.benis_container)
    private val benisGraph: ImageView by bindView(R.id.benis_graph)
    private val actionRules: TextView by bindView(R.id.action_rules)
    private val loginView: TextView by bindView(R.id.action_login)
    private val logoutView: TextView by bindView(R.id.action_logout)
    private val feedbackView: TextView by bindView(R.id.action_contact)
    private val settingsView: TextView by bindView(R.id.action_settings)
    private val inviteView: TextView by bindView(R.id.action_invite)
    private val userImageView: View by bindView(R.id.user_image)

    private val navItemsRecyclerView: RecyclerView by bindView(R.id.drawer_nav_list)

    private val navigationAdapter = NavigationAdapter()
    private val doIfAuthorizedHelper = LoginActivity.helper(this)

    private lateinit var defaultColor: ColorStateList
    private lateinit var markedColor: ColorStateList


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.left_drawer, container, false)
    }

    override fun injectComponent(activityComponent: ActivityComponent) {
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.context.obtainStyledAttributes(intArrayOf(R.attr.colorAccent, android.R.attr.textColorPrimary)).use { result ->
            markedColor = ColorStateList.valueOf(result.getColor(result.getIndex(0), 0))
            defaultColor = ColorStateList.valueOf(result.getColor(result.getIndex(1), 0))
        }

        // add some space on the top for the translucent status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val params = userImageView.layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin += getStatusBarHeight(activity)
        }

        // initialize the top navigation items
        navItemsRecyclerView.adapter = navigationAdapter
        navItemsRecyclerView.layoutManager = LinearLayoutManager(activity)
        navItemsRecyclerView.isNestedScrollingEnabled = false

        // add the static items to the navigation
        navigationAdapter.setNavigationItems(navigationProvider.categoryNavigationItems(null, false))

        settingsView.setOnClickListener({
            val intent = Intent(activity, SettingsActivity::class.java)
            startActivity(intent)
        })

        inviteView.setOnClickListener({
            val intent = Intent(activity, InviteActivity::class.java)
            startActivity(intent)
        })

        actionRules.setOnClickListener({
            val intent = Intent(activity, RulesActivity::class.java)
            startActivity(intent)
        })

        loginView.setOnClickListener({
            val intent = Intent(activity, LoginActivity::class.java)
            startActivity(intent)
        })

        logoutView.setOnClickListener({
            LogoutDialogFragment().show(fragmentManager, null)
        })

        feedbackView.setOnClickListener({
            val intent = Intent(activity, ContactActivity::class.java)
            startActivity(intent)
        })

        benisGraph.setOnClickListener {
            this.onBenisGraphClicked()
        }

        // colorize all the secondary icons.
        val views = listOf(loginView, logoutView, feedbackView, settingsView, inviteView, actionRules)
        for (v in views) {
            val secondary = ColorStateList.valueOf(0x80808080.toInt())
            changeCompoundDrawableColor(v, secondary)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        doIfAuthorizedHelper.onActivityResult(requestCode, resultCode)
    }

    private fun onBenisGraphClicked() {
        DialogBuilder.start(activity)
                .cancelable()
                .content(R.string.benis_graph_explanation)
                .positive()
                .show()
    }

    override fun onResume() {
        super.onResume()

        userService.loginState()
                .compose(bindToLifecycleAsync())
                .onErrorResumeEmpty()
                .subscribe({ this.onLoginStateChanged(it) })

        navigationProvider.navigationItems()
                .distinctUntilChanged()
                .compose<List<NavigationItem>>(bindToLifecycleAsync())
                .subscribe(
                        Action1 { navigationAdapter.setNavigationItems(it) },
                        ErrorDialogFragment.defaultOnError())
    }

    private fun onLoginStateChanged(state: UserService.LoginState) {
        if (state.authorized) {
            usernameView.text = state.name
            usernameView.setOnClickListener { callback.onUsernameClicked() }

            userTypeView.visible = true
            userTypeView.setTextColor(ContextCompat.getColor(context,
                    UserClasses.MarkColors.get(state.mark)))

            userTypeView.text = getString(UserClasses.MarkStrings.get(state.mark)).toUpperCase()
            userTypeView.setOnClickListener { callback.onUsernameClicked() }


            benisView.text = (state.score).toString()

            val benis = state.benisHistory
            if (benis != null && benis.points.size > 2) {
                benisGraph.setImageDrawable(GraphDrawable(benis))
                benisContainer.visible = true

                updateBenisDeltaForGraph(benis)
            } else {
                updateBenisDelta(0)
            }

            loginView.visible = false
            logoutView.visible = true
            actionRules.visible = true
            inviteView.visible = true
        } else {
            usernameView.setText(R.string.pr0gramm)
            usernameView.setOnClickListener(null)

            userTypeView.text = ""
            userTypeView.visible = false

            benisContainer.visible = false
            benisGraph.setImageDrawable(null)

            loginView.visible = true
            logoutView.visible = false
            actionRules.visible = false
            inviteView.visible = false
        }
    }

    private fun updateBenisDeltaForGraph(benis: Graph) {
        val delta = (benis.last.y - benis.first.y).toInt()
        updateBenisDelta(delta)
    }

    private fun updateBenisDelta(delta: Int) {
        benisDeltaView.visible = true
        benisDeltaView.setTextColor(if (delta < 0)
            ContextCompat.getColor(context, R.color.benis_delta_negative)
        else
            ContextCompat.getColor(context, R.color.benis_delta_positive))

        benisDeltaView.text = String.format("%s%d", if (delta < 0) "↓" else "↑", delta)
    }

    fun updateCurrentFilters(current: FeedFilter?) {
        navigationAdapter.setCurrentFilter(current)
    }

    interface OnFeedFilterSelected {
        /**
         * Called if a drawer filter was clicked.

         * @param filter The feed filter that was clicked.
         */
        fun onFeedFilterSelectedInNavigation(filter: FeedFilter)

        /**
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
    }

    private val callback: OnFeedFilterSelected
        get() {
            return activity as OnFeedFilterSelected
        }

    private inner class NavigationAdapter() : RecyclerView.Adapter<NavigationItemViewHolder>() {
        private val allItems = ArrayList<NavigationItem>()
        private var currentFilter: FeedFilter? = null
        private var selected: NavigationItem? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NavigationItemViewHolder {
            val inflater = LayoutInflater.from(parent.context).cloneInContext(parent.context)
            val view = inflater.inflate(viewType, parent, false)
            return NavigationItemViewHolder(view)
        }

        override fun getItemViewType(position: Int): Int {
            return allItems.get(position).layout
        }

        override fun onBindViewHolder(holder: NavigationItemViewHolder, position: Int) {
            val item = allItems.get(position)
            holder.text.text = item.title

            // set the icon of the image
            holder.text.setCompoundDrawablesWithIntrinsicBounds(item.icon, null, null, null)

            // update color
            val color = if ((selected == item)) markedColor else defaultColor
            holder.text.setTextColor(color)
            changeCompoundDrawableColor(holder.text, color.withAlpha(ICON_ALPHA))

            // handle clicks
            holder.itemView.setOnClickListener {
                dispatchItemClick(item)
            }

            holder.itemView.setOnLongClickListener {
                item.bookmark?.let { showDialogToRemoveBookmark(it) }
                true
            }

            if (item.action === NavigationProvider.ActionType.MESSAGES) {
                holder.unread?.text = (item.unreadCount).toString()
                holder.unread?.visibility = if (item.unreadCount > 0) View.VISIBLE else View.GONE
            }
        }

        override fun getItemCount(): Int {
            return allItems.size
        }

        fun setNavigationItems(items: List<NavigationItem>) {
            this.allItems.clear()
            this.allItems.addAll(items)
            merge()
        }

        fun setCurrentFilter(current: FeedFilter?) {
            currentFilter = current
            merge()
        }

        private fun merge() {
            selected = allItems.firstOrNull { it.hasFilter() && it.filter == currentFilter }

            if (selected == null) {
                currentFilter?.let { current ->
                    selected = allItems.firstOrNull { it.filter?.feedType === current.feedType }
                }
            }

            notifyDataSetChanged()
        }
    }

    internal fun dispatchItemClick(item: NavigationItem) {
        when (item.action) {
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
                callback.onNavigateToFavorites(item.filter!!.likes.get())

            NavigationProvider.ActionType.SECRETSANTA ->
                openSecretSanta()
        }
    }

    private fun openSecretSanta() {
        val cookieValue = cookieHandler.loginCookieValue.orNull()

        // check if the cookie is set. If not, set it.
        var javaScript = Joiner.on("").join(ImmutableList.of<String>(
                "if (!/pr0app=UNIQUE/.test(document.cookie)) {",
                "  document.cookie = 'me=" + cookieValue + "';",
                "  document.cookie = 'pr0app=UNIQUE';",
                "  location.reload();",
                "} else {",
                "  setInterval(function() {$('.snowflake').remove();}, 250);",
                "  setInterval(function() {$('.pane.secret-santa').css('padding-bottom', '96px')}, 1000);",
                "}"))

        // use a unique cookie value each time. not sure if this is needed.
        javaScript = javaScript.replace("UNIQUE", (System.currentTimeMillis()).toString())

        val url = "https://pr0gramm.com/secret-santa/iap"
        CustomTabsHelper.newWebviewBuilder(activity)
                .injectJavaScript("javascript:" + javaScript)
                .show(url)

        Track.secretSantaClicked()
    }

    private fun showInboxActivity(unreadCount: Int) {
        showInboxActivity(if (unreadCount == 0) InboxType.ALL else InboxType.UNREAD)
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
        DialogBuilder.start(activity)
                .content(R.string.do_you_want_to_remove_this_bookmark)
                .negative(R.string.cancel)
                .positive(R.string.delete, { bookmarkService.delete(bookmark) })
                .show()
    }

    private class NavigationItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text = (itemView as? TextView ?: itemView.findViewById(R.id.title)) as TextView
        val unread = itemView.findViewById(R.id.unread_count) as? TextView
    }

    companion object {
        private val ICON_ALPHA = 127

        /**
         * Fakes the drawable tint by applying a color filter on all compound
         * drawables of this view.

         * @param view  The view to "tint"
         * *
         * @param color The color with which the drawables are to be tinted.
         */
        private fun changeCompoundDrawableColor(view: TextView, color: ColorStateList) {
            val defaultColor = color.defaultColor
            val drawables = view.compoundDrawables
            for (drawable in drawables) {
                if (drawable == null)
                    continue

                // fake the tint with a color filter.
                drawable.mutate().setColorFilter(defaultColor, PorterDuff.Mode.SRC_IN)
            }
        }
    }
}
