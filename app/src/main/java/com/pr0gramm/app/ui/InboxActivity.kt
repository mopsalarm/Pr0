package com.pr0gramm.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.observe
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.pr0gramm.app.R
import com.pr0gramm.app.services.InboxService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.fragments.ConversationsFragment
import com.pr0gramm.app.ui.fragments.GenericInboxFragment
import com.pr0gramm.app.ui.fragments.WrittenCommentsFragment
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.startActivity
import kotterknife.bindView


/**
 * The activity that displays the inbox.
 */
class InboxActivity : BaseAppCompatActivity("InboxActivity") {
    private val userService: UserService by instance()
    private val inboxService: InboxService by instance()

    private val coordinator: CoordinatorLayout by bindView(R.id.coordinator)
    private val tabLayout: TabLayout by bindView(R.id.tabs)
    private val pager: ViewPager by bindView(R.id.pager)

    private lateinit var tabsAdapter: TabsStateAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.noActionBar)
        super.onCreate(savedInstanceState)

        if (!userService.isAuthorized) {
            startActivity<MainActivity>()
            finish()
            return
        }

        setContentView(R.layout.activity_inbox)

        val toolbar = find<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        tabsAdapter = TabsStateAdapter(this)

        val types = InboxType.values().toMutableList()

        if (!userService.userIsPremium) {
            types -= InboxType.STALK
        }


        types.forEach { type ->
            when (type) {
                InboxType.PRIVATE -> tabsAdapter.addTab(getString(R.string.inbox_type_private), id = InboxType.PRIVATE) {
                    ConversationsFragment()
                }

                InboxType.COMMENTS_OUT -> tabsAdapter.addTab(getString(R.string.inbox_type_comments_out), id = InboxType.COMMENTS_OUT) {
                    WrittenCommentsFragment()
                }

                InboxType.ALL -> tabsAdapter.addTab(getString(R.string.inbox_type_all), id = InboxType.ALL) {
                    GenericInboxFragment()
                }

                InboxType.COMMENTS_IN -> tabsAdapter.addTab(getString(R.string.inbox_type_comments_in), id = InboxType.COMMENTS_IN) {
                    GenericInboxFragment(GenericInboxFragment.MessageTypeComments)
                }

                InboxType.STALK -> tabsAdapter.addTab(getString(R.string.inbox_type_stalk), id = InboxType.STALK) {
                    GenericInboxFragment(GenericInboxFragment.MessageTypeStalk)
                }

                InboxType.NOTIFICATIONS -> tabsAdapter.addTab(getString(R.string.inbox_type_notifications), id = InboxType.NOTIFICATIONS) {
                    GenericInboxFragment(GenericInboxFragment.MessageTypeNotifications)
                }
            }
        }

        pager.adapter = tabsAdapter
        pager.offscreenPageLimit = 1

        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                onTabChanged()
            }
        })

        tabLayout.setupWithViewPager(pager, true)

        // restore previously selected tab
        if (savedInstanceState != null) {
            pager.currentItem = savedInstanceState.getInt("tab")
        } else {
            handleNewIntent(intent)
        }

        // track if we've clicked the notification!
        if (intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            Track.inboxNotificationClosed("clicked")
        }

        intent.getStringExtra(EXTRA_CONVERSATION_NAME)?.let { name ->
            ConversationActivity.start(this, name, skipInbox = true)
        }

        inboxService.unreadMessagesCount().observe(this) { counts ->
            fun titleOf(id: Int, count: Int): String {
                return getString(id) + (if (count > 0) " ($count)" else "")
            }

            tabsAdapter.updateTabTitle(InboxType.ALL, titleOf(R.string.inbox_type_all, counts.total))
            tabsAdapter.updateTabTitle(InboxType.PRIVATE, titleOf(R.string.inbox_type_private, counts.messages))
            tabsAdapter.updateTabTitle(InboxType.NOTIFICATIONS, titleOf(R.string.inbox_type_notifications, counts.notifications))
            tabsAdapter.updateTabTitle(InboxType.STALK, titleOf(R.string.inbox_type_stalk, counts.follows))
            tabsAdapter.updateTabTitle(InboxType.COMMENTS_IN, titleOf(R.string.inbox_type_comments_in, counts.comments))
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item) || when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else -> false
        }
    }

    override fun onStart() {
        super.onStart()
        Track.inboxActivity()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNewIntent(intent)
    }

    private fun handleNewIntent(intent: Intent?) {
        val extras = intent?.extras ?: return
        showInboxType(InboxType.values()[extras.getInt(EXTRA_INBOX_TYPE, 0)])
    }

    private fun showInboxType(type: InboxType?) {
        if (type != null && type.ordinal < tabsAdapter.getItemCount()) {
            pager.currentItem = type.ordinal
        }
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        onTabChanged()
    }

    private fun onTabChanged() {
        val index = pager.currentItem
        if (index >= 0 && index < tabsAdapter.getItemCount()) {
            title = tabsAdapter.getPageTitle(index)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", pager.currentItem)
    }

    companion object {
        const val EXTRA_INBOX_TYPE = "InboxActivity.inboxType"
        const val EXTRA_FROM_NOTIFICATION = "InboxActivity.fromNotification"
        const val EXTRA_CONVERSATION_NAME = "InboxActivity.conversationName"
    }
}
