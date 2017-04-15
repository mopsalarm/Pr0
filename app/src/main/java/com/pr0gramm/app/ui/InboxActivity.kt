package com.pr0gramm.app.ui

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.view.ViewPager
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import com.github.salomonbrys.kodein.instance
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.R
import com.pr0gramm.app.services.*
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.fragments.MessageInboxFragment
import com.pr0gramm.app.ui.fragments.PrivateMessageInboxFragment
import com.pr0gramm.app.ui.fragments.WrittenCommentFragment
import kotterknife.bindView


/**
 * The activity that displays the inbox.
 */
class InboxActivity : BaseAppCompatActivity(), ViewPager.OnPageChangeListener {
    private val userService: UserService by instance()
    private val inboxService: InboxService by instance()
    private val notificationService: NotificationService by instance()

    private val tabLayout: TabLayout by bindView(R.id.tabs)
    private val viewPager: ViewPager by bindView(R.id.pager)

    private lateinit var tabsAdapter: TabsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.noActionBar)
        super.onCreate(savedInstanceState)

        if (!userService.isAuthorized) {
            MainActivity.open(this)
            finish()
            return
        }

        setContentView(R.layout.activity_inbox)

        setSupportActionBar(findViewById(R.id.toolbar) as Toolbar)

        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        tabsAdapter = TabsAdapter(this, this.supportFragmentManager)
        tabsAdapter.addTab(R.string.inbox_type_unread, MessageInboxFragment::class.java,
                MessageInboxFragment.buildArguments(InboxType.UNREAD))

        tabsAdapter.addTab(R.string.inbox_type_all, MessageInboxFragment::class.java,
                MessageInboxFragment.buildArguments(InboxType.ALL))

        tabsAdapter.addTab(R.string.inbox_type_private, PrivateMessageInboxFragment::class.java, null)

        tabsAdapter.addTab(R.string.inbox_type_comments, WrittenCommentFragment::class.java, null)

        viewPager.adapter = tabsAdapter
        viewPager.addOnPageChangeListener(this)

        tabLayout.setupWithViewPager(viewPager)

        // restore previously selected tab
        if (savedInstanceState != null) {
            viewPager.currentItem = savedInstanceState.getInt("tab")
        } else {
            handleNewIntent(intent)
        }

        // track if we've clicked the notification!
        if (intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            Track.notificationClosed("clicked")
        }

        inboxService.markAsRead(intent.getLongExtra(EXTRA_MESSAGE_TIMESTAMP, 0))
    }

    override fun injectComponent(appComponent: ActivityComponent) {
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item) || OptionMenuHelper.dispatch(this, item)
    }

    @OnOptionsItemSelected(android.R.id.home)
    override fun finish() {
        super.finish()
    }

    override fun onResume() {
        super.onResume()

        // remove pending notifications.
        notificationService.cancelForInbox()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNewIntent(intent)
    }

    private fun handleNewIntent(intent: Intent?) {
        if (intent != null && intent.extras != null) {
            val extras = intent.extras
            showInboxType(InboxType.values()[extras.getInt(EXTRA_INBOX_TYPE, 0)])
        }
    }

    private fun showInboxType(type: InboxType?) {
        if (type != null && type.ordinal < tabsAdapter.count) {
            viewPager.currentItem = type.ordinal
        }
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        onTabChanged()
    }

    private fun onTabChanged() {
        val index = viewPager.currentItem
        if (index >= 0 && index < tabsAdapter.count) {
            title = tabsAdapter.getPageTitle(index)
            trackScreen(index)
        }
    }

    /**
     * Might not be the most beautiful code, but works for now.
     */
    private fun trackScreen(index: Int) {
        when (index) {
            0 -> Track.screen("InboxUnread")
            1 -> Track.screen("InboxOverview")
            2 -> Track.screen("InboxPrivate")
            3 -> Track.screen("InboxComments")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", viewPager.currentItem)
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {

    }

    override fun onPageSelected(position: Int) {
        onTabChanged()
    }

    override fun onPageScrollStateChanged(state: Int) {

    }

    companion object {
        val EXTRA_INBOX_TYPE = "InboxActivity.inboxType"
        val EXTRA_FROM_NOTIFICATION = "InboxActivity.fromNotification"
        val EXTRA_MESSAGE_TIMESTAMP = "InboxActivity.maxMessageId"
    }
}
